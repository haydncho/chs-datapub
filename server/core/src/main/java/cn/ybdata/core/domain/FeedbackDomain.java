package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.ReportDomain.handler;
import static cn.ybdata.core.domain.ReportDomain.overlay;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.page.PageOverlay;
import cn.ybdata.core.security.AccessDeniedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 意见与申诉 (A10) and the institution-side 意见核对 (B5) that feeds it.
 *
 * <p>State machine of a feedback item: {@code todo} (待分派) → {@code doing} (处理中, 承办人 set) →
 * {@code done} (已答复, closed). {@code reply} (待答复) is a seed state treated like {@code doing}.
 * 已超期 is not stored: an open item whose 5-working-day SLA has passed is shown as {@code over}.
 * A closed item can neither be re-assigned nor answered again.
 */
@Configuration
public class FeedbackDomain {

    static final int MAX_REPLY = 2000;
    static final int MAX_FIELD = 200;
    static final int MAX_ATTACHMENTS = 5;

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public FeedbackDomain(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** one feedback item with its latest reply */
    record Row(String id, String kind, String orgId, String org, String title, String location, String report,
               String status, String assignee, String body, OffsetDateTime createdAt, String submittedBy,
               String assignedBy, OffsetDateTime assignedAt, String attachments, String source,
               String reply, String repliedBy, OffsetDateTime repliedAt, boolean correction) {

        LocalDate due() {
            return Sla.addWorkingDays(Sla.date(createdAt), Sla.FEEDBACK_DAYS);
        }

        int daysLeft(LocalDate today) {
            return Sla.workingDaysLeft(today, due());
        }

        /** status as shown: open items past their SLA are 已超期 */
        String shownStatus(LocalDate today) {
            if ("done".equals(status)) return "done";
            return daysLeft(today) < 0 ? "over" : status;
        }
    }

    // ── queries ──────────────────────────────────────────────────────────────

    /** @param orgId only this institution's items; null = all */
    List<Row> rows(String orgId, String submittedBy, String source) {
        return jdbc.sql("""
                select f.id, f.kind, f.org_id, f.org_name, f.title, f.location, f.report, f.status, f.assignee, f.body,
                       f.created_at, f.submitted_by, f.assigned_by, f.assigned_at, f.attachments::text, f.source,
                       r.body, r.replied_by, r.replied_at, coalesce(r.triggers_correction, false)
                from feedback_item f
                left join lateral (select body, replied_by, replied_at, triggers_correction from feedback_reply
                                   where item_id = f.id order by replied_at desc, id desc limit 1) r on true
                where (cast(:org as varchar) is null or f.org_id = :org)
                  and (cast(:by as varchar) is null or f.submitted_by = :by)
                  and (cast(:src as varchar) is null or f.source = :src)
                order by f.created_at desc, f.id desc""")
                .param("org", orgId).param("by", submittedBy).param("src", source)
                .query((rs, i) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                        rs.getString(10), rs.getObject(11, OffsetDateTime.class), rs.getString(12), rs.getString(13),
                        rs.getObject(14, OffsetDateTime.class), rs.getString(15), rs.getString(16), rs.getString(17),
                        rs.getString(18), rs.getObject(19, OffsetDateTime.class), rs.getBoolean(20)))
                .list();
    }

    private String status(String id) {
        return jdbc.sql("select status from feedback_item where id = :id").param("id", id)
                .query(String.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("意见单不存在:" + id));
    }

    String nextId() {
        return "YJ-" + jdbc.sql("select nextval('feedback_item_seq')").query(Long.class).single();
    }

    static String required(JsonNode p, String field, String label, int max) {
        JsonNode v = p == null ? null : p.get(field);
        String s = v == null || v.isNull() || !v.isValueNode() ? "" : v.asText().strip();
        if (s.isEmpty()) throw new IllegalArgumentException("请填写" + label);
        if (s.length() > max) throw new IllegalArgumentException(label + "不能超过 " + max + " 字");
        return s;
    }

    // ── A10 actions ──────────────────────────────────────────────────────────

    @Bean
    ActionHandler a10Assign() {
        return handler("A10", "assignFeedback", (actor, p) -> {
            String id = required(p, "id", "意见单编号", 16);
            String assignee = required(p, "assignee", "承办人", 32);
            boolean known = jdbc.sql("select count(*) from app_user where name = :n and enabled")
                    .param("n", assignee).query(Long.class).single() > 0;
            if (!known) throw new IllegalArgumentException("承办人不存在或已停用:" + assignee);
            String st = status(id);
            if ("done".equals(st)) throw new IllegalArgumentException("该意见单已答复闭环,不能改派");
            String current = jdbc.sql("select assignee from feedback_item where id = :id").param("id", id)
                    .query(String.class).optional().orElse(null);
            if (assignee.equals(current)) throw new IllegalArgumentException("该意见单已由 " + assignee + " 承办");
            int n = jdbc.sql("""
                    update feedback_item set status = case when status = 'todo' then 'doing' else status end,
                           assignee = :a, assigned_by = :by, assigned_at = now()
                    where id = :id and status <> 'done'""")
                    .param("a", assignee).param("by", actor).param("id", id).update();
            if (n == 0) throw new IllegalArgumentException("该意见单已答复闭环,不能改派");
            return Map.of("id", id, "assignee", assignee, "assignedBy", actor);
        });
    }

    @Bean
    ActionHandler a10Reply() {
        return handler("A10", "replyFeedback", (actor, p) -> {
            String id = required(p, "id", "意见单编号", 16);
            String text = required(p, "text", "答复内容", MAX_REPLY);
            boolean correction = p.path("triggerCorrection").asBoolean(false);
            status(id); // 404-style message for unknown ids
            // one reply per item: the conditional update is the guard (concurrent replies see 0 rows)
            int n = jdbc.sql("update feedback_item set status = 'done' where id = :id and status <> 'done'")
                    .param("id", id).update();
            if (n == 0) throw new IllegalArgumentException("该意见单已答复闭环,不能重复答复");
            jdbc.sql("insert into feedback_reply (item_id, body, triggers_correction, replied_by) values (:id, :b, :c, :by)")
                    .param("id", id).param("b", text).param("c", correction).param("by", actor).update();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("repliedBy", actor);
            if (correction) {
                String title = jdbc.sql("select title from feedback_item where id = :id").param("id", id).query(String.class).single();
                // an accepted correction enters the publishing workflow as a 更正 task at step 1
                jdbc.sql("insert into publish_task (id, title, kind, step, status) values (:t, :title, '更正', 1, 'open') on conflict do nothing")
                        .param("t", "GZ-" + id).param("title", truncate("更正 · " + title, 128)).update();
                out.put("correctionTask", "GZ-" + id);
            }
            return out;
        });
    }

    static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ── B5 意见核对 ───────────────────────────────────────────────────────────

    record Round(String id, String title, String report, OffsetDateTime deadline) {
        boolean closed() {
            return OffsetDateTime.now().isAfter(deadline);
        }
    }

    Round currentRound() {
        return jdbc.sql("select id, title, report, deadline from verification_round order by deadline desc limit 1")
                .query((rs, i) -> new Round(rs.getString(1), rs.getString(2), rs.getString(3), rs.getObject(4, OffsetDateTime.class)))
                .optional().orElseThrow(() -> new IllegalArgumentException("当前没有待核对的报告"));
    }

    /** id → label of the items in the 核对稿 (the B5 read model) */
    Map<String, JsonNode> verificationItems() {
        String raw = jdbc.sql("select payload->'items' from page_payload where code = 'B5'").query(String.class)
                .optional().orElse("[]");
        Map<String, JsonNode> out = new LinkedHashMap<>();
        try {
            JsonNode arr = json.readTree(raw == null ? "[]" : raw);
            for (JsonNode it : arr) {
                if (it.hasNonNull("id")) out.put(it.get("id").asText(), it);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    /** B5: every item the hospital marks 有差异 becomes a 纠错 item in A10 — once per institution and round. */
    @Bean
    ActionHandler b5Submit() {
        return handler("B5", "submitVerification", (actor, p) -> {
            Caller who = Caller.resolve(jdbc, actor);
            if (!who.hospital()) throw new AccessDeniedException("核对意见只能由定点医药机构本院身份提交,医保局身份不能代机构提交");
            Round round = currentRound();
            if (round.closed()) {
                throw new IllegalArgumentException("核对已于 " + Sla.mdhm(round.deadline()) + " 截止,不能再提交");
            }
            Map<String, JsonNode> known = verificationItems();
            JsonNode items = p.path("items");
            if (!items.isArray() || items.isEmpty()) throw new IllegalArgumentException("请逐项确认核对结果");
            Map<String, ObjectNode> answers = new LinkedHashMap<>();
            for (JsonNode it : items) {
                String id = it.path("id").asText("");
                if (!known.containsKey(id)) throw new IllegalArgumentException("不是本次核对的数据项:" + truncate(id, 32));
                if (answers.containsKey(id)) throw new IllegalArgumentException("数据项重复:" + id);
                String label = known.get(id).path("label").asText(id);
                String result = it.path("result").asText("");
                ObjectNode a = json.createObjectNode().put("id", id).put("result", result);
                if ("diff".equals(result)) {
                    a.put("ownValue", required(it, "ownValue", "「" + label + "」的本院数值", 64));
                    a.put("reason", required(it, "reason", "「" + label + "」的差异原因", MAX_FIELD));
                    a.set("attachments", attachments(it.path("attachments")));
                } else if (!"ok".equals(result)) {
                    throw new IllegalArgumentException("「" + label + "」请选择一致或有差异");
                }
                answers.put(id, a);
            }
            int missing = (int) known.keySet().stream().filter(k -> !answers.containsKey(k)).count();
            if (missing > 0) throw new IllegalArgumentException("还有 " + missing + " 项未确认");

            ArrayNode stored = json.createArrayNode();
            answers.values().forEach(stored::add);
            int first = jdbc.sql("""
                    insert into verification_submission (round_id, org_id, submitted_by, items)
                    values (:r, :o, :by, cast(:items as jsonb)) on conflict (round_id, org_id) do nothing""")
                    .param("r", round.id()).param("o", who.orgId()).param("by", actor).param("items", stored.toString()).update();
            if (first == 0) throw new IllegalArgumentException("本院已提交过本轮核对结果,不能重复提交");

            List<String> created = new ArrayList<>();
            for (ObjectNode a : answers.values()) {
                if (!"diff".equals(a.path("result").asText())) continue;
                JsonNode item = known.get(a.get("id").asText());
                String id = nextId();
                jdbc.sql("""
                        insert into feedback_item (id, kind, org_id, org_name, title, location, report, status, body,
                                                   source, round_id, submitted_by, attachments)
                        values (:id, '纠错', :org, :orgName, :title, '机构核对', :report, 'todo', :body,
                                'b5', :round, :by, cast(:att as jsonb))""")
                        .param("id", id).param("org", who.orgId()).param("orgName", who.orgName() == null ? who.orgId() : who.orgName())
                        .param("title", truncate("核对差异 · " + item.path("label").asText(a.get("id").asText()), 128))
                        .param("report", round.report())
                        .param("body", "核对稿数值 " + item.path("value").asText("—") + " · 本院数值 " + a.get("ownValue").asText()
                                + " · 原因:" + a.get("reason").asText())
                        .param("round", round.id()).param("by", actor)
                        .param("att", names(a.path("attachments")).toString())
                        .update();
                created.add(id);
            }
            return Map.of("created", created);
        });
    }

    /** 佐证材料: file metadata only (name, size, type) — the files themselves stay with the institution */
    private ArrayNode attachments(JsonNode in) {
        ArrayNode out = json.createArrayNode();
        if (in == null || in.isMissingNode() || in.isNull()) return out;
        if (!in.isArray()) throw new IllegalArgumentException("佐证材料格式不正确");
        if (in.size() > MAX_ATTACHMENTS) throw new IllegalArgumentException("每项最多上传 " + MAX_ATTACHMENTS + " 个佐证材料");
        for (JsonNode f : in) {
            String name = required(f, "name", "佐证材料文件名", 100);
            long size = f.path("size").asLong(0);
            if (size < 0 || size > 20L * 1024 * 1024) throw new IllegalArgumentException("佐证材料单个文件不能超过 20MB:" + name);
            out.addObject().put("name", name).put("size", size).put("type", truncate(f.path("type").asText(""), 64));
        }
        return out;
    }

    private ArrayNode names(JsonNode attachments) {
        ArrayNode out = json.createArrayNode();
        attachments.forEach(f -> out.add(f.path("name").asText()));
        return out;
    }

    // ── read models ──────────────────────────────────────────────────────────

    ObjectNode itemJson(ObjectNode o, Row r, LocalDate today) {
        o.put("id", r.id()).put("type", r.kind()).put("org", r.org()).put("title", r.title())
                .put("section", r.location() == null ? "—" : r.location()).put("report", r.report() == null ? "—" : r.report())
                .put("daysLeft", r.daysLeft(today)).put("dueDate", Sla.md(r.due()))
                .put("status", r.shownStatus(today)).put("assignee", r.assignee() == null ? "" : r.assignee())
                .put("text", r.body() == null ? "" : r.body());
        try {
            o.set("attachments", json.readTree(r.attachments() == null ? "[]" : r.attachments()));
        } catch (JsonProcessingException e) {
            o.putArray("attachments");
        }
        ObjectNode t = o.putObject("track");
        t.put("submittedAt", Sla.mdhm(r.createdAt()));
        if (r.submittedBy() != null) t.put("submittedBy", r.submittedBy());
        if (r.assignedAt() != null) t.put("assignedAt", Sla.mdhm(r.assignedAt()));
        if (r.assignedBy() != null) t.put("assignedBy", r.assignedBy());
        if (r.repliedAt() != null) {
            t.put("repliedAt", Sla.mdhm(r.repliedAt())).put("repliedBy", r.repliedBy()).put("reply", r.reply())
                    .put("correction", r.correction());
        }
        return o;
    }

    /** A10 shows the live queue: every item (incl. those raised from B5 / C3), real SLA, track and KPIs. */
    @Bean
    PageOverlay a10Overlay() {
        return overlay("A10", payload -> {
            LocalDate today = Sla.today();
            List<Row> rows = rows(null, null, null);
            ArrayNode items = payload.arrayNode();
            Set<String> seen = new HashSet<>();
            for (Row r : rows) {
                items.add(itemJson(payload.objectNode(), r, today));
                seen.add(r.id());
            }
            if (payload.get("items") instanceof ArrayNode old) {
                old.forEach(n -> {
                    if (!seen.contains(n.path("id").asText())) items.add(n);
                });
            }
            payload.set("items", items);

            long total = rows.size();
            long done = rows.stream().filter(r -> "done".equals(r.status())).count();
            double avg = rows.stream().filter(r -> r.repliedAt() != null)
                    .mapToDouble(r -> java.time.Duration.between(r.createdAt(), r.repliedAt()).toMinutes() / 1440.0)
                    .average().orElse(Double.NaN);
            long corrections = jdbc.sql("select count(*) from publish_task where kind = '更正'").query(Long.class).single();
            ObjectNode stats = payload.putObject("stats");
            stats.put("replyRate", total == 0 ? "—" : String.valueOf(Math.round(done * 100.0 / total)));
            stats.put("avgReplyDays", Double.isNaN(avg) ? "—" : String.format("%.1f", avg));
            stats.put("corrections", String.valueOf(corrections));
            stats.put("slaDays", Sla.FEEDBACK_DAYS);
        });
    }

    /**
     * B5 for the viewing institution: the round's real deadline, this institution's stored submission
     * (so a reload keeps 已提交) and the progress / replies of its own feedback items (答复回流).
     */
    @Bean
    PageOverlay b5Overlay() {
        return overlay("B5", payload -> {
            LocalDate today = Sla.today();
            Round round = jdbc.sql("select id, title, report, deadline from verification_round order by deadline desc limit 1")
                    .query((rs, i) -> new Round(rs.getString(1), rs.getString(2), rs.getString(3), rs.getObject(4, OffsetDateTime.class)))
                    .optional().orElse(null);
            Caller who = Caller.viewer(jdbc, "B5");
            boolean hospital = who != null && who.hospital();
            if (round != null) {
                String due = Sla.mdhm(round.deadline());
                boolean closed = round.closed();
                int left = closed ? 0 : Math.max(0, (int) java.time.temporal.ChronoUnit.DAYS.between(today, Sla.date(round.deadline())));
                payload.put("remainingDays", left);
                payload.put("subtitle", "发布前核对 · 仅本院可见 · 截止 " + due);
                payload.putObject("round").put("id", round.id()).put("deadline", due).put("closed", closed);
            }
            String blocked = null;
            if (!hospital) blocked = "当前为医保局身份,只能查看;核对结果须由机构本院身份提交";
            if (hospital && round != null) {
                jdbc.sql("select submitted_by, submitted_at, items::text from verification_submission where round_id = :r and org_id = :o")
                        .param("r", round.id()).param("o", who.orgId())
                        .query((rs, i) -> {
                            ObjectNode s = payload.putObject("submission");
                            s.put("submittedBy", rs.getString(1)).put("submittedAt", Sla.mdhm(rs.getObject(2, OffsetDateTime.class)));
                            try {
                                s.set("items", json.readTree(rs.getString(3)));
                            } catch (JsonProcessingException e) {
                                s.putArray("items");
                            }
                            return s;
                        }).optional();
            }
            if (blocked == null && payload.has("submission")) blocked = "本院已提交本轮核对结果";
            if (blocked == null && round != null && round.closed()) blocked = "核对已于 " + Sla.mdhm(round.deadline()) + " 截止,逾期未确认视为无异议";
            ObjectNode viewer = payload.putObject("viewer").put("canSubmit", blocked == null);
            if (blocked != null) viewer.put("reason", blocked);
            if (hospital && who.orgName() != null) viewer.put("org", who.orgName());

            ArrayNode progress = payload.putArray("progress");
            if (hospital) {
                for (Row r : rows(who.orgId(), null, null)) progress.add(itemJson(payload.objectNode(), r, today));
            }
        });
    }
}
