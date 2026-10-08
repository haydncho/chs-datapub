package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.PublishRules.APPROVAL_STEP;
import static cn.ybdata.core.domain.PublishRules.ARCHIVED_STEP;
import static cn.ybdata.core.domain.PublishRules.TARGETED_RELEASE_STEP;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 发布工作流 (A8). Step 5 is 召集人审批 — nothing leaves the analysis zone until it is approved
 * (未批准不外发: a report linked to a task is only served to institutions once the task reached step 6).
 *
 * <p>Every change is server state: the approved 定向范围, the 操作日志 (publish_event), 催办, and the
 * 更正 / 撤回 tasks a published task spawns. The A8 overlay serves it back per task.
 */
@Configuration
public class PublishDomain {

    static final Set<String> CONVENER = Set.of("convener");
    static final Set<String> WORKFLOW_ROLES = Set.of("convener", "admin");
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final List<String> GROUPS = List.of("月告知", "季公布", "年通报", "专题", "提醒函", "更正");

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public PublishDomain(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    record Task(String id, String title, String kind, int step, LocalDate due, String status, Integer coverage,
                String scope, String reportId, String originTask, OffsetDateTime approvedAt, boolean demoSeed) {}

    private static final String TASK_COLS =
            "id, title, kind, step, due, status, coverage, scope::text, report_id, origin_task, approved_at, demo_seed";

    private static Task task(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Date due = rs.getDate(5);
        return new Task(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                due == null ? null : due.toLocalDate(), rs.getString(6), (Integer) rs.getObject(7), rs.getString(8),
                rs.getString(9), rs.getString(10), rs.getObject(11, OffsetDateTime.class), rs.getBoolean(12));
    }

    /** the task row, locked for this transaction (concurrent approvals are serialised) */
    Task lock(String taskId) {
        return jdbc.sql("select " + TASK_COLS + " from publish_task where id = :id for update").param("id", taskId)
                .query((rs, i) -> task(rs)).optional()
                .orElseThrow(() -> new IllegalArgumentException("发布任务不存在:" + taskId));
    }

    int stepOf(String taskId) {
        return jdbc.sql("select step from publish_task where id = :id").param("id", taskId).query(Integer.class)
                .optional().orElseThrow(() -> new IllegalArgumentException("发布任务不存在:" + taskId));
    }

    /** the A8 page payload as seeded (institutions, steps, checks) — the reference the approval is validated against */
    ObjectNode a8Seed() {
        String raw = jdbc.sql("select payload::text from page_payload where code = 'A8'").query(String.class).optional()
                .orElse("{}");
        try {
            return (ObjectNode) json.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String stepName(ObjectNode seed, int step) {
        JsonNode s = seed.path("steps").path(step - 1);
        return s.isTextual() ? s.asText() : "第 " + step + " 步";
    }

    void decide(String taskId, String decision, String comment, Integer backTo, String actor) {
        jdbc.sql("insert into publish_decision (task_id, decision, comment, back_to, decided_by) values (:t, :d, :c, :b, :by)")
                .param("t", taskId).param("d", decision).param("c", comment).param("b", backTo).param("by", clip(actor, 32)).update();
    }

    void event(String taskId, String kind, String who, String tag, String what, String target) {
        jdbc.sql("insert into publish_event (task_id, kind, who, tag, what, target) values (:t, :k, :w, :tag, :what, :tg)")
                .param("t", taskId).param("k", kind).param("w", clip(who, 32)).param("tag", clip(tag, 32))
                .param("what", what).param("tg", target == null ? null : clip(target, 64)).update();
    }

    static String clip(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n);
    }

    static String roleTag(Actor a) {
        return a != null && "convener".equals(a.role()) ? "召集人" : "行政管理组";
    }

    // ── 召集人审批 ────────────────────────────────────────────────────────────

    @Bean
    ActionHandler a8Approve() {
        return handler("A8", "approvePublish", (actor, p) -> {
            Actor who = Who.require("仅召集人可批准发布", CONVENER);
            Task t = lock(Json.text(p, "taskId"));
            if (t.step() != APPROVAL_STEP) {
                throw new IllegalArgumentException("该任务当前处于第 " + t.step() + " 步,不在召集人审批节点,请刷新后查看最新状态");
            }
            ObjectNode seed = a8Seed();
            // 发布前检查:任何未通过的阻断项都不能批准
            for (JsonNode c : seed.path("checks")) {
                if (!c.path("ok").asBoolean(false) && !"warn".equals(c.path("level").asText())) {
                    throw new IllegalArgumentException("发布前检查未通过:" + c.path("text").asText());
                }
            }
            // 定向范围:必须至少覆盖 1 家,且都是可发布的机构
            Set<String> known = new HashSet<>();
            seed.path("institutions").forEach(h -> known.add(h.path("name").asText()));
            Set<String> chosen = new LinkedHashSet<>();
            p.path("institutions").forEach(n -> chosen.add(n.asText().trim()));
            chosen.remove("");
            if (chosen.isEmpty()) throw new IllegalArgumentException("定向范围覆盖 0 家机构,不能批准发布");
            List<String> unknown = chosen.stream().filter(n -> !known.isEmpty() && !known.contains(n)).toList();
            if (!unknown.isEmpty()) throw new IllegalArgumentException("定向范围包含未知机构:" + String.join("、", unknown));

            ObjectNode scope = json.createObjectNode();
            ArrayNode inst = scope.putArray("institutions");
            chosen.forEach(inst::add);
            if (p.path("filters").isObject()) scope.set("filters", p.get("filters"));
            jdbc.sql("""
                    update publish_task set step = :s, status = 'approved', coverage = :n, scope = cast(:scope as jsonb),
                           approved_at = now() where id = :id""")
                    .param("s", TARGETED_RELEASE_STEP).param("n", chosen.size()).param("scope", scope.toString())
                    .param("id", t.id()).update();
            String comment = Json.textOr(p, "comment", "").trim();
            String name = Who.name(who, actor);
            decide(t.id(), "approve", comment, null, name);
            event(t.id(), "approve", name, "召集人 · 批准", "批准发布" + (comment.isEmpty() ? "" : ":" + comment), null);
            event(t.id(), "release", "系统", "定向发布",
                    "推送至 " + chosen.size() + " 家机构 · 签收期 " + seed.path("signPeriod").asText("5 个工作日"), null);
            return Map.of("step", TARGETED_RELEASE_STEP, "coverage", chosen.size());
        });
    }

    @Bean
    ActionHandler a8Reject() {
        return handler("A8", "rejectPublish", (actor, p) -> {
            Actor who = Who.require("仅召集人可驳回发布", CONVENER);
            String comment = Json.textOr(p, "comment", "").trim();
            if (comment.isEmpty()) throw new IllegalArgumentException("驳回须填写意见");
            JsonNode toNode = p.path("toStep");
            if (!toNode.canConvertToInt() || toNode.asDouble() != toNode.asInt()) throw new IllegalArgumentException("退回步骤须为 1–4 的整数");
            int to = toNode.asInt();
            if (to < 1 || to >= APPROVAL_STEP) throw new IllegalArgumentException("退回步骤须为 1–4 的整数");
            Task t = lock(Json.text(p, "taskId"));
            if (t.step() != APPROVAL_STEP) {
                throw new IllegalArgumentException("该任务当前处于第 " + t.step() + " 步,不在召集人审批节点,请刷新后查看最新状态");
            }
            jdbc.sql("update publish_task set step = :s, status = 'rejected' where id = :id").param("s", to).param("id", t.id()).update();
            String name = Who.name(who, actor);
            decide(t.id(), "reject", comment, to, name);
            event(t.id(), "reject", name, "召集人 · 驳回", "退回「" + stepName(a8Seed(), to) + "」:" + comment, null);
            return Map.of("step", to);
        });
    }

    /** 提交召集人审批: a task in preparation (incl. a rejected one or a new 更正 task) re-enters the approval node. */
    @Bean
    ActionHandler a8Submit() {
        return handler("A8", "submitForApproval", (actor, p) -> {
            Actor who = Who.require("仅召集人或行政管理组可提交审批", WORKFLOW_ROLES);
            Task t = lock(Json.text(p, "taskId"));
            if (t.step() >= APPROVAL_STEP || "archived".equals(t.status()) || "withdrawn".equals(t.status())) {
                throw new IllegalArgumentException(t.step() == APPROVAL_STEP ? "该任务已在召集人审批节点" : "该任务已过审批节点,不能再次提交");
            }
            jdbc.sql("update publish_task set step = :s, status = 'open' where id = :id")
                    .param("s", APPROVAL_STEP).param("id", t.id()).update();
            String note = Json.textOr(p, "comment", "").trim();
            event(t.id(), "submit", Who.name(who, actor), roleTag(who),
                    ("rejected".equals(t.status()) ? "按驳回意见修改后重新提交召集人审批" : "提交召集人审批") + (note.isEmpty() ? "" : ":" + note), null);
            return Map.of("step", APPROVAL_STEP);
        });
    }

    // ── 签收追踪 · 催办 ─────────────────────────────────────────────────────────

    /** institutions of a released task that have not signed its report yet */
    List<String> unsignedOf(Task t) {
        List<String> inst = institutionsOf(t);
        Set<String> signed = new HashSet<>(signedOf(t, inst));
        return inst.stream().filter(n -> !signed.contains(n)).toList();
    }

    List<String> institutionsOf(Task t) {
        List<String> out = new ArrayList<>();
        if (t.scope() == null) return out;
        try {
            json.readTree(t.scope()).path("institutions").forEach(n -> out.add(n.asText()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    /** targeted institutions whose organisation signed the task's report (report_signoff) */
    List<String> signedOf(Task t, List<String> institutions) {
        if (t.reportId() == null || institutions.isEmpty()) return List.of();
        List<String> orgs = jdbc.sql("select o.name from report_signoff s join org o on o.id = s.org_id where s.report_id = :r")
                .param("r", t.reportId()).query(String.class).list();
        return institutions.stream().filter(i -> orgs.stream().anyMatch(o -> PublishRules.sameInstitution(o, i))).toList();
    }

    @Bean
    ActionHandler a8Urge() {
        return handler("A8", "urgeSign", (actor, p) -> urge(actor, Json.text(p, "taskId"), List.of(Json.text(p, "institution"))));
    }

    @Bean
    ActionHandler a8UrgeAll() {
        return handler("A8", "urgeSignAll", (actor, p) -> urge(actor, Json.text(p, "taskId"), null));
    }

    private Map<String, Object> urge(String actor, String taskId, List<String> only) {
        Actor who = Who.require("仅召集人或行政管理组可催办", WORKFLOW_ROLES);
        Task t = lock(taskId);
        if (t.step() < TARGETED_RELEASE_STEP || t.step() >= ARCHIVED_STEP) throw new IllegalArgumentException("该任务不在签收期,无需催办");
        List<String> unsigned = unsignedOf(t);
        Set<String> already = new HashSet<>(jdbc.sql("select target from publish_event where task_id = :t and kind = 'urge'")
                .param("t", t.id()).query(String.class).list());
        List<String> targets;
        if (only != null) {
            String n = only.get(0);
            if (!unsigned.contains(n)) throw new IllegalArgumentException(n + " 不在未签收名单中");
            targets = List.of(n);
        } else {
            targets = unsigned;
        }
        List<String> sent = new ArrayList<>();
        String name = Who.name(who, actor);
        for (String n : targets) {
            if (already.contains(n)) continue;
            event(t.id(), "urge", name, "催办", "向 " + n + " 发送签收催办 · 政务微信 + 短信", n);
            sent.add(n);
        }
        return Map.of("urged", sent, "unsigned", unsigned.size());
    }

    // ── 更正 / 撤回 ─────────────────────────────────────────────────────────────

    @Bean
    ActionHandler a8StartCorrection() {
        return handler("A8", "startCorrection", (actor, p) -> revise(actor, p, true));
    }

    @Bean
    ActionHandler a8StartWithdraw() {
        return handler("A8", "startWithdraw", (actor, p) -> revise(actor, p, false));
    }

    /** a published task spawns a 更正 / 撤回 task that goes through 专家组审核 and 召集人审批 again */
    private Map<String, Object> revise(String actor, JsonNode p, boolean correction) {
        Actor who = Who.require("仅召集人或行政管理组可发起更正或撤回", WORKFLOW_ROLES);
        Task t = lock(Json.text(p, "taskId"));
        if (t.step() < TARGETED_RELEASE_STEP || "withdrawn".equals(t.status())) {
            throw new IllegalArgumentException("仅已发布的任务可发起更正或撤回");
        }
        int open = jdbc.sql("select count(*) from publish_task where origin_task = :id and step < :a and status not in ('archived', 'withdrawn')")
                .param("id", t.id()).param("a", ARCHIVED_STEP).query(Integer.class).single();
        if (open > 0) throw new IllegalArgumentException("该任务已有进行中的更正或撤回,请先完成");
        int n = jdbc.sql("select count(*) from publish_task where origin_task = :id").param("id", t.id()).query(Integer.class).single() + 1;
        String id = clip((correction ? "GZ-" : "CH-") + t.id() + "-" + n, 32);
        String title = clip(t.title() + (correction ? " · 更正" : " · 撤回"), 128);
        String reason = Json.textOr(p, "reason", "").trim();
        jdbc.sql("""
                insert into publish_task (id, title, kind, step, due, status, origin_task, report_id)
                values (:id, :title, '更正', 4, :due, 'open', :origin, :report)""")
                .param("id", id).param("due", LocalDate.now(ZONE).plusDays(5)).param("title", title).param("origin", t.id()).param("report", t.reportId()).update();
        String name = Who.name(who, actor);
        String what = (correction ? "发起更正" : "发起撤回") + " → " + id + (reason.isEmpty() ? "" : ":" + reason);
        event(t.id(), correction ? "correction" : "withdraw", name, roleTag(who), what, null);
        event(id, "submit", name, roleTag(who), (correction ? "由「" : "撤回「") + t.title() + "」" + (correction ? "发起更正" : "") + ",进入专家组审核", null);
        return Map.of("taskId", id);
    }

    /**
     * Test/demo support only (integration tests, e2e): puts an explicitly flagged demo seed task
     * (publish_task.demo_seed, only m8) back at the approval node, dropping its scope, log, derived tasks and
     * the sign-offs of its report. Convener only; never for archived tasks; absent unless
     * {@code yb.auth.dev-header=true}.
     */
    @Bean
    @ConditionalOnProperty(name = "yb.auth.dev-header", havingValue = "true")
    ActionHandler a8ResetDemo() {
        return handler("A8", "resetDemo", (actor, p) -> {
            Who.require("仅召集人可重置演示任务", CONVENER);
            Task t = lock(Json.text(p, "taskId"));
            if (!t.demoSeed()) throw new IllegalArgumentException("仅演示种子任务可重置");
            if ("archived".equals(t.status()) || t.step() >= ARCHIVED_STEP) throw new IllegalArgumentException("已归档任务不能重置");
            List<String> derived = jdbc.sql("select id from publish_task where origin_task = :id").param("id", t.id()).query(String.class).list();
            for (String d : derived) {
                jdbc.sql("delete from publish_event where task_id = :t").param("t", d).update();
                jdbc.sql("delete from publish_decision where task_id = :t").param("t", d).update();
                jdbc.sql("delete from publish_task where id = :t").param("t", d).update();
            }
            jdbc.sql("delete from publish_event where task_id = :t").param("t", t.id()).update();
            if (t.reportId() != null) jdbc.sql("delete from report_signoff where report_id = :r").param("r", t.reportId()).update();
            jdbc.sql("""
                    update publish_task set step = :s, status = 'open', coverage = null, scope = null, approved_at = null
                    where id = :id""").param("s", APPROVAL_STEP).param("id", t.id()).update();
            return Map.of("step", APPROVAL_STEP);
        });
    }

    // ── read model ──────────────────────────────────────────────────────────

    record Event(String taskId, String kind, String who, String tag, String what, String target, OffsetDateTime at) {}

    /** A8 shows every task in publish_task with its live step, due text, approved scope, log, 催办 and sign-offs. */
    @Bean
    PageOverlay a8Overlay() {
        return overlay("A8", payload -> {
            LocalDate today = LocalDate.now(ZONE);
            Map<String, Task> rows = new LinkedHashMap<>();
            jdbc.sql("select " + TASK_COLS + " from publish_task order by id").query((rs, i) -> task(rs)).list()
                    .forEach(t -> rows.put(t.id(), t));
            Map<String, List<Event>> events = new HashMap<>();
            jdbc.sql("select task_id, kind, who, tag, what, target, at from publish_event order by at, id")
                    .query((rs, i) -> new Event(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6), rs.getObject(7, OffsetDateTime.class)))
                    .list().forEach(e -> events.computeIfAbsent(e.taskId(), k -> new ArrayList<>()).add(e));

            ArrayNode tasks = payload.get("tasks") instanceof ArrayNode a ? a : payload.putArray("tasks");
            Set<String> seen = new HashSet<>();
            for (JsonNode n : tasks) {
                if (n instanceof ObjectNode o && rows.containsKey(o.path("id").asText())) {
                    seen.add(o.path("id").asText());
                    patchTask(o, rows.get(o.path("id").asText()), events, today);
                }
            }
            for (Task t : rows.values()) {
                if (seen.contains(t.id())) continue;
                ObjectNode o = tasks.addObject();
                o.put("id", t.id()).put("group", GROUPS.contains(t.kind()) ? t.kind() : "更正").put("name", t.title());
                patchTask(o, t, events, today);
            }
        });
    }

    private void patchTask(ObjectNode o, Task t, Map<String, List<Event>> events, LocalDate today) {
        o.put("step", t.step());
        o.put("status", t.status());
        o.put("due", PublishRules.dueText(t.due(), today, t.step(), t.status()));
        Integer left = PublishRules.daysLeft(t.due(), today, t.step(), t.status());
        if (left == null) o.putNull("daysLeft");
        else o.put("daysLeft", left);
        if (t.originTask() != null) o.put("origin", t.originTask());
        if (t.reportId() != null) o.put("reportId", t.reportId());
        if (t.approvedAt() != null) o.put("approvedAt", t.approvedAt().atZoneSameInstant(ZONE).toLocalDate().toString());
        List<String> inst = institutionsOf(t);
        if (t.coverage() != null) {
            ObjectNode scope = o.putObject("scope");
            scope.put("coverage", t.coverage());
            ArrayNode a = scope.putArray("institutions");
            inst.forEach(a::add);
            try {
                JsonNode f = json.readTree(t.scope()).path("filters");
                if (f.isObject()) scope.set("filters", f);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
        ArrayNode signed = o.putArray("signed");
        signedOf(t, inst).forEach(signed::add);
        ArrayNode logs = o.putArray("logs");
        ArrayNode urged = o.putArray("urged");
        for (Event e : events.getOrDefault(t.id(), List.of())) {
            if ("urge".equals(e.kind())) {
                if (e.target() != null) urged.add(e.target());
                continue;
            }
            logs.addObject().put("time", e.at().atZoneSameInstant(ZONE).format(LOG_TIME)).put("who", e.who())
                    .put("tag", e.tag()).put("what", e.what());
        }
    }
}
