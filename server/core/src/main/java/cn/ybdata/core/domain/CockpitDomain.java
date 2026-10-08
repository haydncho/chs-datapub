package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.ReportDomain.handler;
import static cn.ybdata.core.domain.ReportDomain.overlay;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.page.PageOverlay;
import cn.ybdata.core.security.AccessDeniedException;
import cn.ybdata.core.security.Actor;
import cn.ybdata.core.security.CurrentActor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 全景图 (cockpit):订阅推送与告警确认处置。
 * <ul>
 *   <li>{@code cockpit/saveSubscription} — 校验后写入 {@code cockpit_subscription}(登录用户 × 视角);
 *       overlay 把当前用户已保存的订阅放进 {@code identities[].savedSubscription}(未保存为 null)。</li>
 *   <li>{@code cockpit/ackAlarm} — 写入 {@code cockpit_alarm_ack}(市医保局共用一份、医院按本院、县区按本县);
 *       overlay 给 {@code identities[].alerts[]} 标上 {@code acked},页面据此不再弹出已确认的告警。</li>
 * </ul>
 * 视角与身份对应:医院身份只能操作 {@code hosp},其余身份只能操作 {@code conv}(开发回退身份不限)。
 */
@Configuration
public class CockpitDomain {

    static final String PAGE = "cockpit";
    /** demo identity of the dev fallback for this page (see action.Actors) */
    static final String DEV_NAME = "陈志远";
    static final String DEMO_ORG = "H001";
    private static final Set<String> IDENTITIES = Set.of("conv", "hosp");

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public CockpitDomain(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // ── who is asking ──

    static Actor currentActor() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) return CurrentActor.get(a.getRequest());
        return null;
    }

    /** subscription owner: the login, or dev:&lt;name&gt; for the legacy dev fallback */
    static String ownerOf(Actor a) {
        if (a != null && a.login() != null) return a.login();
        return "dev:" + (a != null && a.name() != null ? a.name() : DEV_NAME);
    }

    /** 医院身份 → hosp;其余已登录身份 → conv;开发回退身份两个视角都可 */
    static boolean mayUse(Actor a, String identity) {
        if (a == null || !a.enforced() || a.role() == null) return true;
        return "hospital".equals(a.role()) ? "hosp".equals(identity) : "conv".equals(identity);
    }

    /** 告警确认的范围:医院按本院,县区医保按本县,其余市医保局身份共用一份 */
    static String ackScope(Actor a, String identity) {
        String org = a != null && a.orgId() != null ? a.orgId() : null;
        if ("hosp".equals(identity)) return "org:" + (org != null && a.isHospital() ? org : DEMO_ORG);
        if (a != null && a.enforced() && "county".equals(a.role()) && org != null) return "county:" + org;
        return "bureau";
    }

    static String alarmKey(String type, String text) {
        return type + "|" + text;
    }

    static String identityOf(JsonNode p, Actor a) {
        String id = Json.text(p, "identity");
        if (!IDENTITIES.contains(id)) throw new IllegalArgumentException("未知视角: " + id);
        if (!mayUse(a, id)) throw new AccessDeniedException("当前身份不能操作该视角的全景图");
        return id;
    }

    // ── the cockpit page as seeded (option lists, alerts) ──

    JsonNode seed() {
        String raw = jdbc.sql("select payload::text from page_payload where code = :c").param("c", PAGE)
                .query(String.class).optional().orElseThrow(() -> new IllegalStateException("cockpit page not seeded"));
        try {
            return json.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static JsonNode identityNode(JsonNode page, String id) {
        for (JsonNode n : page.path("identities")) if (id.equals(n.path("id").asText())) return n;
        throw new IllegalArgumentException("未知视角: " + id);
    }

    static List<String> texts(JsonNode arr) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : arr) out.add(n.asText());
        return out;
    }

    /** distinct values of payload[field] (string array); every value must be one of {@code allowed} */
    static List<String> pickMany(JsonNode p, String field, List<String> allowed, String emptyMsg, String what) {
        JsonNode arr = p.get(field);
        if (arr != null && !arr.isNull() && !arr.isArray()) throw new IllegalArgumentException(what + "格式不正确");
        Set<String> picked = new LinkedHashSet<>();
        if (arr != null) for (JsonNode n : arr) {
            String v = n.asText("");
            if (!allowed.contains(v)) throw new IllegalArgumentException("不支持的" + what + ": " + v);
            picked.add(v);
        }
        if (picked.isEmpty()) throw new IllegalArgumentException(emptyMsg);
        // keep the order of the option list, not of the request
        return allowed.stream().filter(picked::contains).toList();
    }

    static String pickOne(JsonNode p, String field, List<String> allowed, String what) {
        String v = Json.text(p, field);
        if (!allowed.contains(v)) throw new IllegalArgumentException("不支持的" + what + ": " + v);
        return v;
    }

    // ── actions ──

    @Bean
    ActionHandler cockpitSaveSubscription() {
        return handler(PAGE, "saveSubscription", (actor, p) -> {
            Actor who = currentActor();
            String id = identityOf(p, who);
            JsonNode page = seed();
            JsonNode opts = page.path("subscription");
            String frequency = pickOne(p, "frequency", texts(opts.path("frequencies")), "推送频率");
            String channel = pickOne(p, "channel", texts(opts.path("channels")), "推送渠道");
            List<String> contents = pickMany(p, "contents", texts(opts.path("contents")), "请至少选择 1 项推送内容", "推送内容");
            List<String> recipients = pickMany(p, "recipients", texts(identityNode(page, id).path("recipients")), "请至少选择 1 名接收人", "接收人");
            boolean enabled = !p.has("enabled") || p.get("enabled").asBoolean(true);
            String by = actor.length() > 32 ? actor.substring(0, 32) : actor;
            try {
                jdbc.sql("""
                        insert into cockpit_subscription (owner, identity, enabled, frequency, channel, contents, recipients, updated_by)
                        values (:o, :i, :e, :f, :ch, cast(:ct as jsonb), cast(:r as jsonb), :by)
                        on conflict (owner, identity) do update set enabled = excluded.enabled, frequency = excluded.frequency,
                            channel = excluded.channel, contents = excluded.contents, recipients = excluded.recipients,
                            updated_by = excluded.updated_by, updated_at = now()""")
                        .param("o", ownerOf(who)).param("i", id).param("e", enabled).param("f", frequency).param("ch", channel)
                        .param("ct", json.writeValueAsString(contents)).param("r", json.writeValueAsString(recipients))
                        .param("by", by).update();
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
            return load(ownerOf(who), id);
        });
    }

    @Bean
    ActionHandler cockpitAckAlarm() {
        return handler(PAGE, "ackAlarm", (actor, p) -> {
            Actor who = currentActor();
            String id = identityOf(p, who);
            String type = Json.text(p, "type");
            String text = Json.text(p, "text");
            boolean known = false;
            for (JsonNode a : identityNode(seed(), id).path("alerts")) {
                if (type.equals(a.path("type").asText()) && text.equals(a.path("text").asText())) known = true;
            }
            if (!known) throw new IllegalArgumentException("未知告警: " + type + " · " + text);
            String by = actor.length() > 32 ? actor.substring(0, 32) : actor;
            jdbc.sql("insert into cockpit_alarm_ack (scope, alarm_key, acked_by) values (:s, :k, :by) on conflict do nothing")
                    .param("s", ackScope(who, id)).param("k", alarmKey(type, text)).param("by", by).update();
            return Map.of("acked", true, "scope", ackScope(who, id));
        });
    }

    /** the saved subscription as the page shows it, or null */
    Map<String, Object> load(String owner, String identity) {
        return jdbc.sql("""
                select enabled, frequency, channel, contents::text, recipients::text, to_char(updated_at, 'YYYY-MM-DD HH24:MI')
                from cockpit_subscription where owner = :o and identity = :i""")
                .param("o", owner).param("i", identity)
                .query((rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("enabled", rs.getBoolean(1));
                    m.put("frequency", rs.getString(2));
                    m.put("contents", readList(rs.getString(4)));
                    m.put("channel", rs.getString(3));
                    m.put("recipients", readList(rs.getString(5)));
                    m.put("updatedAt", rs.getString(6));
                    return m;
                }).optional().orElse(null);
    }

    private List<String> readList(String raw) {
        try {
            return texts(json.readTree(raw));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ── read model ──

    @Bean
    PageOverlay cockpitOverlay() {
        return overlay(PAGE, payload -> {
            Actor who = currentActor();
            String owner = ownerOf(who);
            for (JsonNode n : payload.path("identities")) {
                if (!(n instanceof ObjectNode idn)) continue;
                String id = idn.path("id").asText();
                Map<String, Object> saved = mayUse(who, id) ? load(owner, id) : null;
                idn.set("savedSubscription", saved == null ? json.nullNode() : json.valueToTree(saved));
                Set<String> acked = new HashSet<>(jdbc.sql("select alarm_key from cockpit_alarm_ack where scope = :s")
                        .param("s", ackScope(who, id)).query(String.class).list());
                if (idn.path("alerts") instanceof ArrayNode alerts) {
                    for (JsonNode a : alerts) {
                        if (a instanceof ObjectNode o) {
                            o.put("acked", acked.contains(alarmKey(o.path("type").asText(), o.path("text").asText())));
                        }
                    }
                }
            }
        });
    }
}
