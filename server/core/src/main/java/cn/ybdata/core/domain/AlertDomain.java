package cn.ybdata.core.domain;

import static cn.ybdata.core.domain.ReportDomain.handler;
import static cn.ybdata.core.domain.ReportDomain.overlay;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.page.PageOverlay;
import cn.ybdata.core.security.AccessDeniedException;
import cn.ybdata.core.security.CountyAlertScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 预警提醒 (A11) → 提醒函 → institution 回执 (D1).
 *
 * <p>State machine: {@code unsent} (未处置) → {@code sent} (提醒函已发, by the 医保局) → {@code ack} (已回执, by
 * the alerted institution itself). A reminder is only sent for an existing alert or one the rule engine
 * really raises; a receipt only for an alert addressed to the receipting institution that is in {@code sent}.
 */
@Configuration
public class AlertDomain {

    private static final Pattern LIVE_ID = Pattern.compile("^AL-(R\\d{2})-(H\\d{3})$");

    private final JdbcClient jdbc;
    private final CountyAlertScope county;

    public AlertDomain(JdbcClient jdbc, CountyAlertScope county) {
        this.jdbc = jdbc;
        this.county = county;
    }

    record Alert(String id, String orgId, String orgName, String metric, String value, String rule, String level,
                 String status, OffsetDateTime sentAt, String sentBy) {}

    record Receipt(String category, String body, OffsetDateTime at, String by) {}

    private static final String COLS = "id, org_id, org_name, metric, value, rule, level, status, sent_at, sent_by";

    private static Alert map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Alert(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getString(8), rs.getObject(9, OffsetDateTime.class), rs.getString(10));
    }

    Optional<Alert> find(String id) {
        return jdbc.sql("select " + COLS + " from alert where id = :id").param("id", id).query((rs, i) -> map(rs)).optional();
    }

    List<Alert> all() {
        return jdbc.sql("select " + COLS + " from alert order by id").query((rs, i) -> map(rs)).list();
    }

    // ── A11 发送提醒函 ─────────────────────────────────────────────────────────

    @Bean
    ActionHandler a11SendReminder() {
        return handler("A11", "sendReminder", (actor, p) -> {
            String id = FeedbackDomain.required(p, "alertId", "预警编号", 16);
            Caller who = Caller.resolve(jdbc, actor);
            Alert a = find(id).orElseGet(() -> createFromRuleEngine(id));
            if (who.county()) {
                String d = county.districtOf(who.orgId(), who.orgName());
                if (!county.orgNamesInDistrict(d).contains(a.orgName())) {
                    throw new AccessDeniedException("只能对本县区机构的预警发送提醒函");
                }
            }
            int n = jdbc.sql("update alert set status = 'sent', sent_at = now(), sent_by = :by where id = :id and status = 'unsent'")
                    .param("by", actor).param("id", a.id()).update();
            if (n == 0) throw new IllegalArgumentException("该预警已发送提醒函或已回执,不能重复发送");
            return Map.of("alertId", a.id(), "org", a.orgName());
        });
    }

    /**
     * An alert computed live by the analytics rule engine ({@code AL-R06-H001}) gets its row when the first
     * 提醒函 is sent — only if the rule really fires for that institution; org / metric / level come from the
     * rule and the org table, never from the client.
     */
    private Alert createFromRuleEngine(String id) {
        Matcher m = LIVE_ID.matcher(id);
        if (!m.matches()) throw new IllegalArgumentException("预警不存在:" + id);
        AlertRules.Hit hit = AlertRules.evaluate(jdbc, m.group(1), m.group(2))
                .orElseThrow(() -> new IllegalArgumentException("预警不存在或本期未触发:" + id));
        Optional<String> same = jdbc.sql("select id from alert where org_id = :o and metric = :m")
                .param("o", hit.orgId()).param("m", hit.rule().label()).query(String.class).optional();
        if (same.isPresent()) throw new IllegalArgumentException("该预警已登记为 " + same.get());
        jdbc.sql("""
                insert into alert (id, org_id, org_name, metric, value, rule, level, status)
                values (:id, :o, :on, :m, :v, :r, :l, 'unsent')""")
                .param("id", id).param("o", hit.orgId()).param("on", hit.orgName()).param("m", hit.rule().label())
                .param("v", FeedbackDomain.truncate(hit.value(), 16)).param("r", FeedbackDomain.truncate(hit.condition(), 32))
                .param("l", hit.rule().level()).update();
        return find(id).orElseThrow();
    }

    // ── D1 回执 ────────────────────────────────────────────────────────────────

    /**
     * D1 receipts reference the alert by its title (e.g. "IU29 例均基金差额超阈值", optionally {@code alertId});
     * only alerts addressed to the caller's own institution are considered.
     */
    @Bean
    ActionHandler d1SubmitReceipt() {
        return handler("D1", "submitReceipt", (actor, p) -> {
            String title = FeedbackDomain.required(p, "alert", "回执对应的预警", 64);
            String category = FeedbackDomain.required(p, "category", "原因类别", 32);
            String text = FeedbackDomain.required(p, "text", "回执说明", 500);
            Caller who = Caller.resolve(jdbc, actor);
            if (!who.hospital()) throw new AccessDeniedException("回执只能由被提醒机构本院身份提交");
            String alertId = p.path("alertId").isTextual() ? p.path("alertId").asText() : null;

            List<Alert> candidates = all().stream().filter(a -> matches(a, title, alertId)).toList();
            if (candidates.isEmpty()) throw new IllegalArgumentException("预警不存在:" + title);
            List<Alert> own = candidates.stream().filter(a -> who.orgId().equals(a.orgId())).toList();
            if (own.isEmpty()) throw new AccessDeniedException("只能对发给本机构的预警提交回执");
            Alert a = own.stream().filter(x -> "sent".equals(x.status())).findFirst().orElse(own.get(0));
            switch (a.status()) {
                case "sent" -> { }
                case "ack" -> throw new IllegalArgumentException("该预警已回执,不能重复提交");
                case "unsent" -> throw new IllegalArgumentException("该预警尚未发送提醒函,无需回执");
                default -> throw new IllegalArgumentException("该预警当前状态不能回执");
            }
            int n = jdbc.sql("update alert set status = 'ack' where id = :id and status = 'sent'").param("id", a.id()).update();
            if (n == 0) throw new IllegalArgumentException("该预警已回执,不能重复提交");
            jdbc.sql("insert into alert_receipt (alert_id, category, body, org_id, submitted_by) values (:id, :c, :b, :o, :by)")
                    .param("id", a.id()).param("c", category).param("b", text).param("o", who.orgId()).param("by", actor).update();
            return Map.of("alertId", a.id());
        });
    }

    /** "IU29 例均基金差额超阈值" names the alert "IU29 例均基金差额"; an explicit alertId wins */
    static boolean matches(Alert a, String title, String alertId) {
        if (alertId != null && !alertId.isBlank()) return alertId.equals(a.id());
        String t = title.strip();
        if (t.startsWith(a.metric())) return true;
        String code = t.split("\\s+")[0];
        String metricCode = a.metric().split("\\s+")[0];
        return code.matches("[A-Z]{2}\\d{2}") && code.equals(metricCode);
    }

    // ── read model ─────────────────────────────────────────────────────────────

    Map<String, Receipt> receipts() {
        Map<String, Receipt> out = new LinkedHashMap<>();
        jdbc.sql("""
                select distinct on (alert_id) alert_id, category, body, received_at, submitted_by
                from alert_receipt order by alert_id, received_at desc, id desc""")
                .query((rs, i) -> Map.entry(rs.getString(1), new Receipt(rs.getString(2), rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class), rs.getString(5))))
                .list().forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    /** A11: live status, 提醒函 date, the institution's real 回执, alerts first raised live, real 回执率. */
    @Bean
    PageOverlay a11Overlay() {
        return overlay("A11", payload -> {
            List<Alert> rows = all();
            Map<String, Receipt> rc = receipts();
            Map<String, Alert> byId = new LinkedHashMap<>();
            rows.forEach(a -> byId.put(a.id(), a));
            if (!(payload.get("alerts") instanceof ArrayNode arr)) return;
            Set<String> seen = new HashSet<>();
            for (JsonNode n : arr) {
                if (!(n instanceof ObjectNode o)) continue;
                seen.add(o.path("id").asText());
                Alert a = byId.get(o.path("id").asText());
                if (a != null) patch(o, a, rc.get(a.id()));
            }
            for (Alert a : rows) {
                if (seen.contains(a.id())) continue;
                ObjectNode o = arr.addObject();
                o.put("id", a.id()).put("org", a.orgName()).put("metric", a.metric()).put("value", a.value())
                        .put("threshold", a.rule()).put("level", a.level());
                trend(o, a);
                patch(o, a, rc.get(a.id()));
            }
            int sent = 0;
            int ack = 0;
            for (JsonNode n : arr) {
                String st = n.path("status").asText();
                if ("sent".equals(st)) sent++;
                if ("ack".equals(st)) ack++;
            }
            payload.put("ackRate", sent + ack == 0 ? 0 : Math.round(ack * 100f / (sent + ack)));
        });
    }

    private static void patch(ObjectNode o, Alert a, Receipt r) {
        o.put("status", a.status());
        if (a.orgId() != null) o.put("orgId", a.orgId());
        if (a.sentAt() != null) {
            LocalDate sent = Sla.date(a.sentAt());
            o.put("sentAt", Sla.md(sent)).put("receiptDue", Sla.md(Sla.addWorkingDays(sent, Sla.RECEIPT_DAYS)));
        }
        if (a.sentBy() != null) o.put("sentBy", a.sentBy());
        if (r != null && "ack".equals(a.status())) {
            ObjectNode x = o.putObject("receipt");
            x.put("category", r.category() == null ? "" : r.category()).put("text", r.body() == null ? "" : r.body());
            if (r.at() != null) x.put("at", Sla.md(Sla.date(r.at())));
            if (r.by() != null) x.put("by", r.by());
        }
    }

    /** 12-month trend for an alert row that is not in the seed payload (from indicator_series via its rule) */
    private void trend(ObjectNode o, Alert a) {
        var rule = jdbc.sql("select metric, unit, threshold from alert_rule where label = :l limit 1").param("l", a.metric())
                .query((rs, i) -> new Object[] {rs.getString(1), rs.getString(2), rs.getObject(3) == null ? null : rs.getDouble(3)})
                .optional();
        ArrayNode t = o.putArray("trend");
        double th = 0;
        if (rule.isPresent() && a.orgId() != null) {
            jdbc.sql("""
                    select value from (select period, value from indicator_series where org_id = :o and metric = :m
                                       order by period desc limit 12) s order by period""")
                    .param("o", a.orgId()).param("m", (String) rule.get()[0])
                    .query(Double.class).list().forEach(t::add);
            o.put("unit", (String) rule.get()[1]);
            th = rule.get()[2] == null ? 0 : (Double) rule.get()[2];
        } else {
            o.put("unit", "");
        }
        while (t.size() < 2) t.insert(0, t.isEmpty() ? 0 : t.get(0).asDouble());
        o.put("thresholdValue", th);
    }
}
