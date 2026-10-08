package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.ReportDomain.handler;
import static cn.ybdata.core.domain.ReportDomain.overlay;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.page.PageOverlay;
import cn.ybdata.core.security.Actor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 流程设计器 (A9): 「发布新版本」 stores the edited flow (nodes with 承办角色 / 时限 / 超时动作 / 通知渠道) as
 * the next version in flow_version; the A9 overlay serves the newest version of each flow and its history.
 */
@Configuration
public class FlowDomain {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public FlowDomain(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    record Version(String flow, int version, String nodes, int total, String note, String by, OffsetDateTime at) {}

    List<Version> versions() {
        return jdbc.sql("select flow, version, nodes::text, total, note, published_by, published_at from flow_version order by flow, version desc")
                .query((rs, i) -> new Version(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getInt(4), rs.getString(5),
                        rs.getString(6), rs.getObject(7, OffsetDateTime.class)))
                .list();
    }

    ObjectNode seed() {
        String raw = jdbc.sql("select payload::text from page_payload where code = 'A9'").query(String.class).optional().orElse("{}");
        try {
            return (ObjectNode) json.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static Set<String> texts(JsonNode arr) {
        Set<String> s = new HashSet<>();
        arr.forEach(n -> s.add(n.asText()));
        return s;
    }

    @Bean
    ActionHandler a9Publish() {
        return handler("A9", "publishFlowVersion", (actor, p) -> {
            Actor who = Who.require("仅召集人或行政管理组可发布流程版本", PublishDomain.WORKFLOW_ROLES);
            String flow = Json.text(p, "flow");
            ObjectNode seed = seed();
            JsonNode seedFlow = null;
            for (JsonNode f : seed.path("flows")) if (flow.equals(f.path("name").asText())) seedFlow = f;
            if (seedFlow == null) throw new IllegalArgumentException("未知流程:" + flow);
            // serialise publishers of the same flow, then check the version the editor started from
            jdbc.sql("select pg_advisory_xact_lock(hashtext(:k))").param("k", "flow:" + flow).query(Object.class).list();
            int stored = jdbc.sql("select coalesce(max(version), 0) from flow_version where flow = :f").param("f", flow)
                    .query(Integer.class).single();
            int current = stored > 0 ? stored : seedFlow.path("version").asInt(1);
            int asked = p.path("version").asInt(-1);
            if (asked != current + 1) {
                throw new IllegalArgumentException("流程已更新为 v" + current + ",请刷新后在最新版本上修改");
            }
            JsonNode nodes = p.path("nodes");
            List<String> problems = FlowRules.problems(nodes, texts(seed.path("lanes")), texts(seed.path("timeoutActions")),
                    texts(seed.path("channels")), seed.path("legalLimit").asInt(15));
            if (!problems.isEmpty()) throw new IllegalArgumentException("流程校验未通过:" + String.join(";", problems));
            ArrayNode clean = json.createArrayNode();
            for (JsonNode n : nodes) {
                ObjectNode o = clean.addObject();
                o.put("lane", n.path("lane").asText()).put("col", n.path("col").asInt()).put("name", n.path("name").asText().trim())
                        .put("kind", n.path("kind").asText()).put("days", n.path("days").asInt());
                if (n.has("timeoutAction")) o.put("timeoutAction", n.path("timeoutAction").asText());
                if (n.has("channels")) {
                    ArrayNode ch = o.putArray("channels");
                    n.path("channels").forEach(c -> ch.add(c.asText()));
                }
            }
            int total = FlowRules.criticalPath(clean);
            String note = PublishDomain.clip(Json.textOr(p, "note", "").trim(), 128);
            jdbc.sql("insert into flow_version (flow, version, nodes, total, note, published_by) values (:f, :v, cast(:n as jsonb), :t, :note, :by)")
                    .param("f", flow).param("v", current + 1).param("n", clean.toString()).param("t", total)
                    .param("note", note.isEmpty() ? "节点配置调整" : note).param("by", PublishDomain.clip(Who.name(who, actor), 32)).update();
            return Map.of("flow", flow, "version", current + 1, "total", total);
        });
    }

    /** each flow shows its newest published version; {@code flows[].versions} lists that flow's history */
    @Bean
    PageOverlay a9Overlay() {
        return overlay("A9", payload -> {
            Map<String, List<Version>> byFlow = new HashMap<>();
            for (Version v : versions()) byFlow.computeIfAbsent(v.flow(), k -> new ArrayList<>()).add(v);
            JsonNode seedVersions = payload.path("versions");
            for (JsonNode f : payload.path("flows")) {
                if (!(f instanceof ObjectNode fo)) continue;
                List<Version> vs = byFlow.getOrDefault(fo.path("name").asText(), List.of());
                ArrayNode hist = fo.putArray("versions");
                for (int i = 0; i < vs.size(); i++) {
                    Version v = vs.get(i);
                    hist.addObject().put("label", "v" + v.version() + (i == 0 ? " · 当前" : ""))
                            .put("date", v.at().atZoneSameInstant(PublishDomain.ZONE).toLocalDate().toString())
                            .put("note", (v.note() == null ? "" : v.note()) + " · " + v.by()).put("current", i == 0);
                }
                for (JsonNode sv : seedVersions) {
                    ObjectNode o = ((ObjectNode) sv).deepCopy();
                    if (!vs.isEmpty()) {
                        o.put("current", false);
                        o.put("label", o.path("label").asText().replace(" · 当前", ""));
                    }
                    hist.add(o);
                }
                if (!vs.isEmpty()) {
                    try {
                        fo.set("nodes", json.readTree(vs.get(0).nodes()));
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }
                    fo.put("version", vs.get(0).version());
                }
            }
        });
    }
}
