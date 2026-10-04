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

/** 预警提醒 (A11) → 提醒函 → institution 回执 (D1). */
@Configuration
public class AlertDomain {

    private final JdbcClient jdbc;

    public AlertDomain(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Bean
    ActionHandler a11SendReminder() {
        return handler("A11", "sendReminder", (actor, p) -> {
            String id = Json.text(p, "alertId");
            // alerts raised by the analytics rule engine (e.g. AL-R07-H001) have no row yet
            int created = jdbc.sql("""
                    insert into alert (id, org_name, metric, value, rule, level, status)
                    values (:id, :org, :metric, '', '', 'mid', 'sent') on conflict (id) do nothing""")
                    .param("id", id).param("org", Json.textOr(p, "org", "—"))
                    .param("metric", Json.textOr(p, "metric", id)).update();
            if (created == 1) return null;
            int n = jdbc.sql("update alert set status = 'sent' where id = :id and status = 'unsent'")
                    .param("id", id).update();
            if (n == 0) throw new IllegalArgumentException("alert not in 未处置 state");
            return null;
        });
    }

    /** D1 receipts reference the alert by its title (e.g. "IU29 例均基金差额超阈值"); match on the DRG code. */
    @Bean
    ActionHandler d1SubmitReceipt() {
        return handler("D1", "submitReceipt", (actor, p) -> {
            String title = Json.text(p, "alert");
            String code = title.split("\\s+")[0];
            String id = jdbc.sql("select id from alert where metric like :m order by id limit 1")
                    .param("m", code + "%").query(String.class).optional()
                    .orElseThrow(() -> new IllegalArgumentException("unknown alert: " + title));
            jdbc.sql("insert into alert_receipt (alert_id, category, body) values (:id, :c, :b)")
                    .param("id", id).param("c", Json.textOr(p, "category", null)).param("b", Json.textOr(p, "text", "")).update();
            jdbc.sql("update alert set status = 'ack' where id = :id").param("id", id).update();
            return Map.of("alertId", id);
        });
    }

    @Bean
    PageOverlay a11Overlay() {
        return overlay("A11", payload -> {
            Map<String, String> st = jdbc.sql("select id, status from alert")
                    .query((rs, i) -> Map.entry(rs.getString(1), rs.getString(2))).list().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            Json.patchArray(payload, "alerts", "id", st, (o, s) -> o.put("status", s));
        });
    }
}
