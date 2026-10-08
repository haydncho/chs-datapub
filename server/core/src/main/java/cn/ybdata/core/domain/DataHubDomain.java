package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.ReportDomain.handler;
import static cn.ybdata.core.domain.ReportDomain.overlay;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.page.PageOverlay;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 数据归集中心 (A3). State in {@code page_state}:
 * {@code source:<name>} → {attempts, arrived, arrivedAt, notifiedAt}; {@code period} → {qcDone, qcBy, qcAt, reportTaskId}.
 *
 * <p>重新拉取 goes to the provincial exchange interface; in this demo deployment the first attempt of a
 * late source times out (HTTP 504, server-generated trace) and the retry succeeds. 生成月度报告 needs the
 * quality check and writes a draft task into {@code publish_task} (A8, step 3 分析成稿).
 */
@Configuration
public class DataHubDomain {

    static final String PAGE = "A3";
    static final List<String> CHANNELS = List.of("政务微信", "短信", "邮件");
    /** A8 step of a freshly generated report draft: 1 计划选题 · 2 归集校验 done → 3 分析成稿 */
    static final int DRAFT_STEP = 3;
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern PERIOD = Pattern.compile("(\\d{4})年(\\d{1,2})月");

    private final JdbcClient jdbc;
    private final PageState state;

    public DataHubDomain(JdbcClient jdbc, PageState state) {
        this.jdbc = jdbc;
        this.state = state;
    }

    private Map<String, ObjectNode> sources() {
        Map<String, ObjectNode> out = new LinkedHashMap<>();
        for (JsonNode s : state.seed(PAGE).path("sources")) {
            if (s instanceof ObjectNode o && o.hasNonNull("name")) out.put(o.get("name").asText(), o);
        }
        return out;
    }

    private ObjectNode source(String name) {
        ObjectNode s = sources().get(name);
        if (s == null) throw new IllegalArgumentException("未知数据源:" + name);
        return s;
    }

    private boolean arrived(String name) {
        return state.get(PAGE, "source:" + name).map(n -> n.path("arrived").asBoolean()).orElse(false);
    }

    /** sources that are late in the seed and have not arrived since */
    private List<String> stillLate() {
        return sources().entrySet().stream()
                .filter(e -> "late".equals(e.getValue().path("status").asText()) && !arrived(e.getKey()))
                .map(Map.Entry::getKey).toList();
    }

    /** "2026年8月期" → ["2026-08", "2026年8月"] */
    static String[] periodKey(String period) {
        Matcher m = PERIOD.matcher(period == null ? "" : period);
        if (!m.find()) throw new IllegalArgumentException("账期格式不正确:" + period);
        int month = Integer.parseInt(m.group(2));
        return new String[] {m.group(1) + String.format("%02d", month), m.group(1) + "年" + month + "月"};
    }

    private String period(JsonNode p) {
        String seeded = state.seed(PAGE).path("period").asText("");
        String asked = Json.textOr(p, "period", seeded);
        if (!asked.equals(seeded)) throw new IllegalArgumentException("只能操作当前账期 " + seeded);
        return seeded;
    }

    @Bean
    ActionHandler a3RetryPull() {
        return handler(PAGE, "retryPull", (actor, p) -> {
            String name = Checks.text(p, "source", "数据源", 32, null);
            ObjectNode src = source(name);
            if (!"late".equals(src.path("status").asText())) throw new IllegalArgumentException(name + " 本期已到数,无需重新拉取");
            ObjectNode st = state.getOrNew(PAGE, "source:" + name);
            if (st.path("arrived").asBoolean()) throw new IllegalArgumentException(name + " 已重新拉取成功,无需重复操作");
            int attempt = st.path("attempts").asInt(0) + 1;
            st.put("attempts", attempt);
            String at = ZonedDateTime.now(cn.ybdata.core.audit.AuditTypes.ZONE).format(HMS);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("attempt", attempt);
            if (attempt == 1) {
                // provincial exchange timed out: the UI shows this trace so the contact can look it up
                String hex = UUID.randomUUID().toString().replace("-", "");
                String trace = hex.substring(0, 4) + "-" + hex.substring(4, 8);
                st.put("lastError", "HTTP 504").put("lastTrace", trace).put("lastAt", at);
                out.put("arrived", false);
                out.put("status", 504);
                out.put("trace", trace);
                out.put("at", at);
            } else {
                st.put("arrived", true).put("arrivedAt", at);
                st.remove(List.of("lastError", "lastTrace", "lastAt"));
                out.put("arrived", true);
                out.put("at", at);
                out.put("dependents", src.path("dependents").size());
            }
            state.put(PAGE, "source:" + name, st, actor);
            return out;
        });
    }

    @Bean
    ActionHandler a3NotifyContact() {
        return handler(PAGE, "notifyContact", (actor, p) -> {
            String name = Checks.text(p, "source", "数据源", 32, null);
            source(name);
            List<String> channels = Checks.texts(p, "channels", "通知渠道", 3, 8, null);
            if (channels.isEmpty() || !CHANNELS.containsAll(channels)) {
                throw new IllegalArgumentException("通知渠道须为 " + String.join(" / ", CHANNELS));
            }
            String at = ZonedDateTime.now(cn.ybdata.core.audit.AuditTypes.ZONE).format(HMS);
            ObjectNode st = state.getOrNew(PAGE, "source:" + name);
            st.put("notifiedAt", at).put("notifiedBy", actor);
            state.put(PAGE, "source:" + name, st, actor);
            return Map.of("notifiedAt", at);
        });
    }

    @Bean
    ActionHandler a3CompleteQualityCheck() {
        return handler(PAGE, "completeQualityCheck", (actor, p) -> {
            period(p);
            ObjectNode st = state.getOrNew(PAGE, "period");
            if (st.path("qcDone").asBoolean()) throw new IllegalArgumentException("本期质量校验已完成");
            List<String> late = stillLate();
            if (!late.isEmpty()) throw new IllegalArgumentException(String.join("、", late) + " 尚未到数,不能完成质量校验");
            st.put("qcDone", true).put("qcBy", actor)
                    .put("qcAt", ZonedDateTime.now(cn.ybdata.core.audit.AuditTypes.ZONE).format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
            state.put(PAGE, "period", st, actor);
            return Map.of("qcDone", true);
        });
    }

    @Bean
    ActionHandler a3GenerateMonthlyReport() {
        return handler(PAGE, "generateMonthlyReport", (actor, p) -> {
            String period = period(p);
            ObjectNode st = state.getOrNew(PAGE, "period");
            if (!st.path("qcDone").asBoolean()) throw new IllegalArgumentException("请先完成数据到数与质量校验");
            String[] key = periodKey(period);
            String taskId = "mr-" + key[0];
            String title = key[1] + " DRG月度运行报告(归集草稿)";
            int created = jdbc.sql("""
                    insert into publish_task (id, title, kind, step, due, status)
                    values (:id, :title, '月告知', :step, null, 'open') on conflict (id) do nothing""")
                    .param("id", taskId).param("title", title).param("step", DRAFT_STEP).update();
            st.put("reportTaskId", taskId);
            state.put(PAGE, "period", st, actor);
            return Map.of("taskId", taskId, "title", title, "step", DRAFT_STEP, "created", created > 0);
        });
    }

    /** Test/demo support (e2e F1): back to the seeded A3 state. Absent when the demo header is off. */
    @Bean
    @ConditionalOnProperty(name = "yb.auth.dev-header", havingValue = "true", matchIfMissing = true)
    ActionHandler a3ResetDemo() {
        return handler(PAGE, "resetDemo", (actor, p) -> {
            state.get(PAGE, "period").map(n -> n.path("reportTaskId").asText(null)).ifPresent(id -> {
                jdbc.sql("delete from publish_decision where task_id = :id").param("id", id).update();
                jdbc.sql("delete from publish_task where id = :id").param("id", id).update();
            });
            state.clear(PAGE);
            return null;
        });
    }

    /** sources that arrived after a re-pull become 已到数 (this period's cell: late), plus the period flags */
    @Bean
    PageOverlay a3Overlay() {
        return overlay(PAGE, payload -> {
            Map<String, ObjectNode> st = state.all(PAGE, "source:");
            Json.patchArray(payload, "sources", "name", st, (o, s) -> {
                if (s.path("arrived").asBoolean() && "late".equals(o.path("status").asText())) {
                    o.put("status", "ok");
                    o.put("arrivedLate", true);
                    o.remove("overdueDays");
                    if (o.get("history") instanceof ArrayNode h && !h.isEmpty()) h.set(h.size() - 1, "late");
                }
                if (s.hasNonNull("notifiedAt")) o.put("notifiedAt", s.get("notifiedAt").asText());
                if (s.hasNonNull("lastTrace")) {
                    o.putObject("lastPull").put("error", s.path("lastError").asText()).put("trace", s.get("lastTrace").asText())
                            .put("at", s.path("lastAt").asText());
                }
            });
            state.get(PAGE, "period").ifPresent(s -> {
                payload.put("qcDone", s.path("qcDone").asBoolean());
                if (s.hasNonNull("qcAt")) payload.put("qcAt", s.get("qcAt").asText());
                if (s.hasNonNull("reportTaskId")) payload.put("reportTaskId", s.get("reportTaskId").asText());
            });
        });
    }
}
