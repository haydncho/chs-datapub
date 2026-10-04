package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.ReportDomain.handler;
import static cn.ybdata.core.domain.ReportDomain.overlay;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.page.PageOverlay;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 意见与申诉 (A10) and the institution-side 意见核对 (B5) that feeds it. */
@Configuration
public class FeedbackDomain {

    private final JdbcClient jdbc;

    public FeedbackDomain(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Item(String id, String kind, String org, String title, String location, String report,
                String status, String assignee, String body) {}

    @Bean
    ActionHandler a10Assign() {
        return handler("A10", "assignFeedback", (actor, p) -> {
            int n = jdbc.sql("update feedback_item set status = 'doing', assignee = :a where id = :id")
                    .param("a", Json.text(p, "assignee")).param("id", Json.text(p, "id")).update();
            if (n == 0) throw new IllegalArgumentException("unknown feedback item");
            return null;
        });
    }

    @Bean
    ActionHandler a10Reply() {
        return handler("A10", "replyFeedback", (actor, p) -> {
            String id = Json.text(p, "id");
            boolean correction = p.path("triggerCorrection").asBoolean(false);
            int n = jdbc.sql("update feedback_item set status = 'done' where id = :id").param("id", id).update();
            if (n == 0) throw new IllegalArgumentException("unknown feedback item");
            jdbc.sql("insert into feedback_reply (item_id, body, triggers_correction, replied_by) values (:id, :b, :c, :by)")
                    .param("id", id).param("b", Json.textOr(p, "text", "")).param("c", correction).param("by", actor).update();
            if (correction) {
                // an accepted correction enters the publishing workflow as a 更正 task at step 1
                jdbc.sql("insert into publish_task (id, title, kind, step, status) values (:t, :title, '更正', 1, 'open') on conflict do nothing")
                        .param("t", "GZ-" + id).param("title", "更正 · " + id).update();
            }
            return correction ? Map.of("correctionTask", "GZ-" + id) : null;
        });
    }

    /** B5: every item the hospital marks 有差异 becomes a 纠错 item in A10. */
    @Bean
    ActionHandler b5Submit() {
        return handler("B5", "submitVerification", (actor, p) -> {
            List<String> created = new java.util.ArrayList<>();
            for (JsonNode it : p.path("items")) {
                if (!"diff".equals(it.path("result").asText())) continue;
                String id = "YJ-" + (1000 + jdbc.sql("select count(*) from feedback_item").query(Integer.class).single());
                jdbc.sql("""
                        insert into feedback_item (id, kind, org_name, title, location, report, status, body)
                        values (:id, '纠错', '示例市第一人民医院', :title, '机构核对', 'BR25 专题核对稿', 'todo', :body)""")
                        .param("id", id)
                        .param("title", "核对差异 · " + Json.textOr(it, "id", "数据项"))
                        .param("body", "本院数值 " + Json.textOr(it, "ownValue", "—") + " · 原因:" + Json.textOr(it, "reason", "—"))
                        .update();
                created.add(id);
            }
            return Map.of("created", created);
        });
    }

    /** A10 shows live status/assignee and any items raised from B5. */
    @Bean
    PageOverlay a10Overlay() {
        return overlay("A10", payload -> {
            Map<String, Item> rows = jdbc.sql("select id, kind, org_name, title, location, report, status, assignee, body from feedback_item order by created_at")
                    .query((rs, i) -> new Item(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9)))
                    .list().stream().collect(Collectors.toMap(Item::id, x -> x, (a, b) -> a, java.util.LinkedHashMap::new));
            Json.patchArray(payload, "items", "id", rows, (o, r) -> {
                o.put("status", r.status());
                o.put("assignee", r.assignee() == null ? "" : r.assignee());
            });
            if (payload.get("items") instanceof ArrayNode arr) {
                Set<String> seen = new HashSet<>();
                arr.forEach(n -> seen.add(n.path("id").asText()));
                rows.values().stream().filter(r -> !seen.contains(r.id())).forEach(r -> {
                    var o = arr.insertObject(0);
                    o.put("id", r.id()).put("type", r.kind()).put("org", r.org()).put("title", r.title())
                            .put("section", r.location()).put("report", r.report()).put("daysLeft", 7)
                            .put("status", r.status()).put("assignee", r.assignee() == null ? "" : r.assignee())
                            .put("text", r.body());
                    o.putArray("attachments");
                });
            }
        });
    }
}
