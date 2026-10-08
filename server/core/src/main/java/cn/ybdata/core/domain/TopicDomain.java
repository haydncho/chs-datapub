package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.ReportDomain.handler;
import static cn.ybdata.core.domain.ReportDomain.overlay;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.audit.AuditTypes;
import cn.ybdata.core.page.PageOverlay;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 智能推荐 (A6) and 病种专题工作台 (A7).
 *
 * <p>A6 state {@code topic:<id>} → {status adopted|skip, title, kind, code, score, facts, taskId}. 采纳 / 本期不做 /
 * 恢复待评估 are reserved for 召集人 and 行政管理组 (推荐仅供参考,由行政管理组采纳). 采纳 opens a 专题 task in
 * {@code publish_task} at step 3 分析成稿 (BR25 reuses the seeded {@code br25} task).
 *
 * <p>A7 state {@code review:<topicId>} → {approved, resolved, pushed, submitted, versions, exports}. 7/7 审定后提交
 * moves the task to step 4 专家组审核, once; 推送 creates 意见 items in A10 for comments not yet pushed;
 * 重新生成 puts an approved section back to 待审定. The analyst identity may not export the 意见单 (具名明细,
 * 未经审核) — only reviewed aggregates leave the controlled environment.
 */
@Configuration
public class TopicDomain {

    static final String A6 = "A6";
    static final String A7 = "A7";
    static final Set<String> ADOPTERS = Set.of("convener", "admin");
    static final List<String> KINDS = List.of("病种专题", "机构专题", "质量专题", "区域专题");
    static final Pattern TOPIC_ID = Pattern.compile("^T-[A-Za-z0-9]{2,16}(-[a-z]{1,8})?$");
    static final int SECTIONS = 7;
    static final int STEP_DRAFT = 3;
    static final int STEP_REVIEW = 4;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final JdbcClient jdbc;
    private final PageState state;

    public TopicDomain(JdbcClient jdbc, PageState state) {
        this.jdbc = jdbc;
        this.state = state;
    }

    // ── topics ──

    static String codeOf(String topicId) {
        String c = topicId.startsWith("T-") ? topicId.substring(2) : topicId;
        int dash = c.indexOf('-');
        return dash > 0 ? c.substring(0, dash) : c;
    }

    /** the A8 task of a topic: BR25 has the seeded 专题 task, others get tp-<id> */
    static String taskIdOf(String topicId) {
        if ("T-BR25".equals(topicId)) return "br25";
        return "tp-" + topicId.substring(2).toLowerCase(Locale.ROOT);
    }

    private Map<String, ObjectNode> seededTopics() {
        Map<String, ObjectNode> out = new LinkedHashMap<>();
        for (JsonNode t : state.seed(A6).path("topics")) {
            if (t instanceof ObjectNode o && o.hasNonNull("id")) out.put(o.get("id").asText(), o);
        }
        return out;
    }

    private String statusOf(String id) {
        return state.get(A6, "topic:" + id).map(s -> s.path("status").asText())
                .orElseGet(() -> {
                    ObjectNode t = seededTopics().get(id);
                    return t == null ? "open" : t.path("status").asText("open");
                });
    }

    /** A6 topic from the seed, else from the payload (live 病种专题 scored by the analytics service) */
    private ObjectNode topicInfo(JsonNode p) {
        String id = Checks.text(p, "id", "选题编号", 32, null);
        if (!TOPIC_ID.matcher(id).matches()) throw new IllegalArgumentException("选题编号格式不正确:" + id);
        ObjectNode seeded = seededTopics().get(id);
        ObjectNode o = state.json().createObjectNode();
        o.put("id", id).put("code", codeOf(id));
        if (seeded != null) {
            o.put("title", seeded.path("title").asText()).put("kind", seeded.path("kind").asText())
                    .put("score", seeded.path("score").asInt());
            o.set("facts", seeded.path("facts").deepCopy());
            return o;
        }
        o.put("title", Checks.text(p, "title", "选题名称", 64, Checks.NAME));
        o.put("kind", Checks.oneOf(p, "kind", "选题类别", KINDS));
        o.put("score", Checks.index(p, "score", "综合得分", 0, 100));
        ArrayNode facts = o.putArray("facts");
        JsonNode f = p.path("facts");
        if (f.isArray()) {
            if (f.size() > 6) throw new IllegalArgumentException("选题要点最多 6 项");
            for (JsonNode x : f) {
                String k = x.path("k").asText(""), v = x.path("v").asText("");
                if (k.isBlank() || k.length() > 12 || v.length() > 16 || v.isBlank()) throw new IllegalArgumentException("选题要点格式不正确");
                facts.addObject().put("k", k).put("v", v);
            }
        }
        return o;
    }

    private static String now(DateTimeFormatter f) {
        return ZonedDateTime.now(AuditTypes.ZONE).format(f);
    }

    private ObjectNode decide(String actor, JsonNode p, String from, String to) {
        PageState.requireRole(ADOPTERS, "推荐仅供参考,须由召集人或行政管理组采纳 / 处理;当前身份无权操作");
        ObjectNode t = topicInfo(p);
        String id = t.get("id").asText();
        String cur = statusOf(id);
        if (!from.equals(cur)) {
            throw new IllegalArgumentException(switch (cur) {
                case "adopted" -> "该选题已采纳,专题工作台进行中";
                case "skip" -> "该选题本期不做,如需处理请先恢复为待评估";
                default -> "该选题当前为待评估";
            });
        }
        t.put("status", to).put("by", actor).put("at", now(STAMP));
        return t;
    }

    @Bean
    ActionHandler a6AdoptTopic() {
        return handler(A6, "adoptTopic", (actor, p) -> {
            ObjectNode t = decide(actor, p, "open", "adopted");
            String id = t.get("id").asText();
            String taskId = taskIdOf(id);
            String title = t.get("title").asText() + " 专题";
            jdbc.sql("insert into publish_task (id, title, kind, step, due, status) values (:id, :t, '专题', :s, null, 'open') on conflict (id) do nothing")
                    .param("id", taskId).param("t", title.length() > 128 ? title.substring(0, 128) : title)
                    .param("s", STEP_DRAFT).update();
            t.put("taskId", taskId);
            state.put(A6, "topic:" + id, t, actor);
            return Map.of("id", id, "status", "adopted", "taskId", taskId);
        });
    }

    @Bean
    ActionHandler a6SkipTopic() {
        return handler(A6, "skipTopic", (actor, p) -> {
            ObjectNode t = decide(actor, p, "open", "skip");
            state.put(A6, "topic:" + t.get("id").asText(), t, actor);
            return Map.of("id", t.get("id").asText(), "status", "skip");
        });
    }

    @Bean
    ActionHandler a6ReopenTopic() {
        return handler(A6, "reopenTopic", (actor, p) -> {
            ObjectNode t = decide(actor, p, "skip", "open");
            state.put(A6, "topic:" + t.get("id").asText(), t, actor);
            return Map.of("id", t.get("id").asText(), "status", "open");
        });
    }

    @Bean
    PageOverlay a6Overlay() {
        return overlay(A6, payload -> {
            Map<String, ObjectNode> st = state.all(A6, "topic:");
            Json.patchArray(payload, "topics", "id", st, (o, s) -> o.put("status", s.path("status").asText()));
            ObjectNode states = payload.putObject("topicStates");
            st.forEach((id, s) -> states.put(id, s.path("status").asText()));
        });
    }

    // ── A7 review ──

    private String defaultTopic() {
        return "T-" + state.seed(A7).path("topic").path("code").asText("BR25");
    }

    /** adopted topics: the seed's adopted ones plus everything adopted in A6 */
    private Map<String, ObjectNode> adopted() {
        Map<String, ObjectNode> out = new LinkedHashMap<>();
        seededTopics().forEach((id, t) -> {
            if ("adopted".equals(t.path("status").asText())) {
                ObjectNode o = state.json().createObjectNode().put("id", id).put("code", codeOf(id))
                        .put("title", t.path("title").asText()).put("kind", t.path("kind").asText())
                        .put("score", t.path("score").asInt()).put("taskId", taskIdOf(id));
                o.set("facts", t.path("facts").deepCopy());
                out.put(id, o);
            }
        });
        state.all(A6, "topic:").forEach((id, s) -> {
            if ("adopted".equals(s.path("status").asText())) out.put(id, s);
            else out.remove(id);
        });
        return out;
    }

    private String topic(JsonNode p) {
        String id = Json.textOr(p, "topic", defaultTopic());
        if (!TOPIC_ID.matcher(id).matches()) throw new IllegalArgumentException("专题编号格式不正确:" + id);
        if (!id.equals(defaultTopic()) && !adopted().containsKey(id)) throw new IllegalArgumentException("该选题尚未采纳,不能进入专题工作台:" + id);
        return id;
    }

    private boolean isDefault(String topicId) {
        return topicId.equals(defaultTopic());
    }

    private ArrayNode seedComments(String topicId) {
        return isDefault(topicId) && state.seed(A7).path("comments") instanceof ArrayNode a ? a : state.json().createArrayNode();
    }

    /** review state of a topic, initialised from the seed (default topic) or empty */
    ObjectNode review(String topicId) {
        return state.get(A7, "review:" + topicId).orElseGet(() -> {
            ObjectNode r = state.json().createObjectNode();
            ArrayNode ap = r.putArray("approved");
            ArrayNode rs = r.putArray("resolved");
            if (isDefault(topicId)) {
                state.seed(A7).path("approved").forEach(ap::add);
                ArrayNode cs = seedComments(topicId);
                for (int k = 0; k < cs.size(); k++) if (cs.get(k).path("resolved").asBoolean()) rs.add(k);
            }
            r.putObject("pushed");
            r.put("submitted", false);
            r.putArray("versions");
            return r;
        });
    }

    private static Set<Integer> ints(JsonNode arr) {
        Set<Integer> s = new TreeSet<>();
        arr.forEach(n -> s.add(n.asInt()));
        return s;
    }

    private static void setInts(ObjectNode r, String field, Set<Integer> s) {
        ArrayNode a = r.putArray(field);
        s.forEach(a::add);
    }

    private void requireOpen(ObjectNode r) {
        if (r.path("submitted").asBoolean()) throw new IllegalArgumentException("已提交核对与审核,稿件已锁定");
    }

    private int section(JsonNode p) {
        return Checks.index(p, "section", "段落", 0, SECTIONS - 1);
    }

    @Bean
    ActionHandler a7ApproveSection() {
        return handler(A7, "approveSection", (actor, p) -> {
            String id = topic(p);
            int sec = section(p);
            ObjectNode r = review(id);
            requireOpen(r);
            Set<Integer> ap = ints(r.path("approved"));
            if (!ap.add(sec)) throw new IllegalArgumentException("第 " + (sec + 1) + " 段已审定");
            setInts(r, "approved", ap);
            state.put(A7, "review:" + id, r, actor);
            return Map.of("approved", ap);
        });
    }

    @Bean
    ActionHandler a7RegenerateSection() {
        return handler(A7, "regenerateSection", (actor, p) -> {
            String id = topic(p);
            int sec = section(p);
            ObjectNode r = review(id);
            requireOpen(r);
            Set<Integer> ap = ints(r.path("approved"));
            boolean wasApproved = ap.remove(sec);
            setInts(r, "approved", ap);
            ArrayNode versions = r.get("versions") instanceof ArrayNode va ? va : r.putArray("versions");
            String title = String.format("§%02d 重新生成", sec + 1);
            versions.insertObject(0).put("title", title)
                    .put("meta", now(STAMP) + " · " + actor + " · 基于最新批次重新生成初稿" + (wasApproved ? ",原审定已撤销,待重新审定" : ""))
                    .put("current", true);
            state.put(A7, "review:" + id, r, actor);
            return Map.of("approved", ap, "reset", wasApproved);
        });
    }

    @Bean
    ActionHandler a7ResolveComment() {
        return handler(A7, "resolveComment", (actor, p) -> {
            String id = topic(p);
            ArrayNode cs = seedComments(id);
            int k = Checks.index(p, "comment", "批注", 0, Math.max(0, cs.size() - 1));
            if (cs.isEmpty()) throw new IllegalArgumentException("该专题没有批注");
            ObjectNode r = review(id);
            Set<Integer> rs = ints(r.path("resolved"));
            if (!rs.add(k)) throw new IllegalArgumentException("该批注已处理");
            setInts(r, "resolved", rs);
            state.put(A7, "review:" + id, r, actor);
            return Map.of("resolved", rs);
        });
    }

    @Bean
    ActionHandler a7PushComments() {
        return handler(A7, "pushComments", (actor, p) -> {
            String id = topic(p);
            ArrayNode cs = seedComments(id);
            ObjectNode r = review(id);
            Set<Integer> rs = ints(r.path("resolved"));
            ObjectNode pushed = r.get("pushed") instanceof ObjectNode po ? po : r.putObject("pushed");
            JsonNode sections = state.seed(A7).path("sections");
            String code = codeOf(id);
            List<String> created = new ArrayList<>();
            for (int k = 0; k < cs.size(); k++) {
                if (rs.contains(k) || pushed.has(String.valueOf(k))) continue;
                JsonNode c = cs.get(k);
                int sec = c.path("section").asInt();
                String where = String.format("§%02d %s", sec + 1, sections.path(sec).path("name").asText(""));
                String fid = nextFeedbackId();
                String text = c.path("text").asText();
                jdbc.sql("""
                        insert into feedback_item (id, kind, org_name, title, location, report, status, body)
                        values (:id, '意见', :org, :title, :loc, :report, 'todo', :body)""")
                        .param("id", fid).param("org", trim(c.path("who").asText("专题工作组"), 64))
                        .param("title", trim(text, 128)).param("loc", trim(where, 64))
                        .param("report", trim(code + " 专题核对稿", 128)).param("body", text).update();
                pushed.put(String.valueOf(k), fid);
                created.add(fid);
            }
            if (created.isEmpty()) throw new IllegalArgumentException("没有待推送的批注(已处理或已推送的不会重复推送)");
            r.put("pushDocNo", Json.textOr(p, "docNo", ""));
            state.put(A7, "review:" + id, r, actor);
            return Map.of("count", created.size(), "ids", created);
        });
    }

    private String nextFeedbackId() {
        int n = jdbc.sql("select count(*) from feedback_item").query(Integer.class).single();
        for (int i = 0; i < 1000; i++) {
            String id = "YJ-" + (1000 + n + i);
            boolean taken = jdbc.sql("select count(*) from feedback_item where id = :id").param("id", id).query(Integer.class).single() > 0;
            if (!taken) return id;
        }
        throw new IllegalStateException("no free feedback id");
    }

    private static String trim(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    @Bean
    ActionHandler a7ExportCommentsExcel() {
        return handler(A7, "exportCommentsExcel", (actor, p) -> {
            if ("analyst".equals(PageState.currentRole())) {
                throw new cn.ybdata.core.security.AccessDeniedException(
                        "受控分析环境仅可导出审核后的聚合结果;意见单含机构具名明细且未经审核,请由行政管理组导出");
            }
            String id = topic(p);
            if (seedComments(id).isEmpty()) throw new IllegalArgumentException("该专题暂无批注,无需导出意见单");
            ObjectNode r = review(id);
            int seq = r.path("exports").asInt(0) + 1;
            r.put("exports", seq);
            state.put(A7, "review:" + id, r, actor);
            String doc = Json.textOr(p, "docNo", "YJD");
            String trace = trim(doc, 24) + "-E" + String.format("%02d", seq);
            return Map.of("traceNo", trace, "watermark", actor + " · " + now(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " · " + trace);
        });
    }

    @Bean
    ActionHandler a7SubmitReview() {
        return handler(A7, "submitReview", (actor, p) -> {
            String id = topic(p);
            ObjectNode r = review(id);
            if (r.path("submitted").asBoolean()) throw new IllegalArgumentException("已提交,机构核对与专家组审核进行中,请勿重复提交");
            Set<Integer> ap = ints(r.path("approved"));
            if (ap.size() < SECTIONS) throw new IllegalArgumentException("还有 " + (SECTIONS - ap.size()) + " 段未审定");
            r.put("submitted", true).put("submittedBy", actor).put("submittedAt", now(STAMP));
            state.put(A7, "review:" + id, r, actor);
            String task = taskIdOf(id);
            jdbc.sql("update publish_task set step = :to where id = :id and step = :from")
                    .param("to", STEP_REVIEW).param("id", task).param("from", STEP_DRAFT).update();
            Integer step = jdbc.sql("select step from publish_task where id = :id").param("id", task).query(Integer.class).optional().orElse(STEP_REVIEW);
            return Map.of("taskId", task, "step", step);
        });
    }

    /** Test/demo support (e2e F2, IT): forget A6 decisions and A7 reviews, drop what they created. */
    @Bean
    @ConditionalOnProperty(name = "yb.auth.dev-header", havingValue = "true", matchIfMissing = true)
    ActionHandler a7ResetDemo() {
        return handler(A7, "resetDemo", (actor, p) -> {
            state.all(A7, "review:").values().forEach(r -> r.path("pushed").forEach(f -> {
                jdbc.sql("delete from feedback_reply where item_id = :id").param("id", f.asText()).update();
                jdbc.sql("delete from feedback_item where id = :id").param("id", f.asText()).update();
            }));
            state.all(A6, "topic:").keySet().forEach(id -> {
                String task = taskIdOf(id);
                if (task.startsWith("tp-")) {
                    jdbc.sql("delete from publish_decision where task_id = :id").param("id", task).update();
                    jdbc.sql("delete from publish_task where id = :id").param("id", task).update();
                }
            });
            jdbc.sql("update publish_task set step = :s, status = 'open' where id = 'br25'").param("s", STEP_DRAFT).update();
            state.clear(A6);
            state.clear(A7);
            return null;
        });
    }

    @Bean
    PageOverlay a7Overlay() {
        return overlay(A7, payload -> {
            Map<String, ObjectNode> adopted = adopted();
            ObjectNode topics = payload.putObject("topics");
            adopted.forEach(topics::set);
            ObjectNode reviews = payload.putObject("reviews");
            state.all(A7, "review:").forEach(reviews::set);
            ObjectNode steps = payload.putObject("taskSteps");
            Set<String> ids = new java.util.LinkedHashSet<>(adopted.keySet());
            ids.add(defaultTopic());
            for (String id : ids) {
                jdbc.sql("select step from publish_task where id = :id").param("id", taskIdOf(id)).query(Integer.class)
                        .optional().ifPresent(s -> steps.put(id, s));
            }
            // the default topic's review also patches the seed fields (approved / comments[].resolved)
            ObjectNode r = reviews.get(defaultTopic()) instanceof ObjectNode o ? o : null;
            if (r != null) {
                payload.set("approved", r.path("approved").deepCopy());
                Set<Integer> rs = ints(r.path("resolved"));
                JsonNode cs = payload.path("comments");
                for (int k = 0; k < cs.size(); k++) {
                    if (cs.get(k) instanceof ObjectNode c) {
                        c.put("resolved", rs.contains(k));
                        JsonNode f = r.path("pushed").get(String.valueOf(k));
                        if (f != null) c.put("pushedAs", f.asText());
                    }
                }
            }
        });
    }
}
