package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.ReportDomain.handler;
import static cn.ybdata.core.domain.ReportDomain.overlay;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.page.PageOverlay;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 发布工作流 (A8). Step 5 is 召集人审批 — nothing leaves the analysis zone
 * until it is approved (未批准不外发).
 */
@Configuration
public class PublishDomain {

    static final int APPROVAL_STEP = 5;
    static final int TARGETED_RELEASE_STEP = 6;

    private final JdbcClient jdbc;

    public PublishDomain(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    int stepOf(String taskId) {
        return jdbc.sql("select step from publish_task where id = :id").param("id", taskId).query(Integer.class)
                .optional().orElseThrow(() -> new IllegalArgumentException("unknown task " + taskId));
    }

    void decide(String taskId, String decision, String comment, Integer backTo, String actor) {
        jdbc.sql("insert into publish_decision (task_id, decision, comment, back_to, decided_by) values (:t, :d, :c, :b, :by)")
                .param("t", taskId).param("d", decision).param("c", comment).param("b", backTo).param("by", actor).update();
    }

    @Bean
    ActionHandler a8Approve() {
        return handler("A8", "approvePublish", (actor, p) -> {
            String id = Json.text(p, "taskId");
            if (stepOf(id) != APPROVAL_STEP) throw new IllegalArgumentException("task is not awaiting approval");
            jdbc.sql("update publish_task set step = :s, status = 'approved' where id = :id")
                    .param("s", TARGETED_RELEASE_STEP).param("id", id).update();
            decide(id, "approve", Json.textOr(p, "comment", ""), null, actor);
            return Map.of("step", TARGETED_RELEASE_STEP);
        });
    }

    @Bean
    ActionHandler a8Reject() {
        return handler("A8", "rejectPublish", (actor, p) -> {
            String id = Json.text(p, "taskId");
            String comment = Json.textOr(p, "comment", "").trim();
            int to = p.path("toStep").asInt(0);
            if (comment.isEmpty()) throw new IllegalArgumentException("驳回须填写意见");
            if (to < 1 || to >= APPROVAL_STEP) throw new IllegalArgumentException("toStep must be 1–4");
            if (stepOf(id) != APPROVAL_STEP) throw new IllegalArgumentException("task is not awaiting approval");
            jdbc.sql("update publish_task set step = :s, status = 'rejected' where id = :id").param("s", to).param("id", id).update();
            decide(id, "reject", comment, to, actor);
            return Map.of("step", to);
        });
    }

    /**
     * Test/demo support only (integration tests, e2e): puts the task back at the approval node.
     * Not exposed in the UI, and absent when the demo header is off (production).
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "yb.auth.dev-header", havingValue = "true", matchIfMissing = true)
    ActionHandler a8ResetDemo() {
        return handler("A8", "resetDemo", (actor, p) -> {
            jdbc.sql("update publish_task set step = :s, status = 'open' where id = :id")
                    .param("s", APPROVAL_STEP).param("id", Json.text(p, "taskId")).update();
            return null;
        });
    }

    @Bean
    PageOverlay a8Overlay() {
        return overlay("A8", payload -> {
            Map<String, Integer> steps = jdbc.sql("select id, step from publish_task")
                    .query((rs, i) -> Map.entry(rs.getString(1), rs.getInt(2))).list().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            Json.patchArray(payload, "tasks", "id", steps, (o, s) -> o.put("step", s));
        });
    }
}
