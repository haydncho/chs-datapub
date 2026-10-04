package cn.ybdata.core.domain;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.page.PageOverlay;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 报告签收: B4 (web) and D1 (mobile) sign the same report rows. */
@Configuration
public class ReportDomain {

    /** fallback when the actor has no organisation (demo identities) — 示例市第一人民医院 */
    static final String DEMO_ORG = "H001";
    /** D1 shows the current monthly report under a hospital-facing title */
    static final String CURRENT_MONTHLY = "R-2026-08";

    private final JdbcClient jdbc;

    public ReportDomain(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void sign(String reportId, String actor, String channel) {
        int n = jdbc.sql("update report set status = 'signed' where id = :id and status in ('sign', 'signed')")
                .param("id", reportId).update();
        if (n == 0) throw new IllegalArgumentException("report not awaiting sign-off: " + reportId);
        jdbc.sql("""
                insert into report_signoff (report_id, org_id, signed_by, channel) values (:r, :o, :by, :ch)
                on conflict (report_id, org_id) do nothing""")
                .param("r", reportId).param("o", orgOf(actor)).param("by", actor).param("ch", channel).update();
    }

    /** the signing institution is the actor's own organisation */
    String orgOf(String actor) {
        return jdbc.sql("select org_id from app_user where (name = :a or login = :a) and org_id like 'H%' limit 1")
                .param("a", actor).query(String.class).optional().orElse(DEMO_ORG);
    }

    String idByTitle(String title) {
        return jdbc.sql("select id from report where title = :t").param("t", title).query(String.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("unknown report: " + title));
    }

    @Bean
    ActionHandler b4SignReport() {
        return handler("B4", "signReport", (actor, p) -> {
            sign(idByTitle(Json.text(p, "name")), actor, "web");
            return null;
        });
    }

    @Bean
    ActionHandler d1SignReport() {
        return handler("D1", "signReport", (actor, p) -> {
            sign(CURRENT_MONTHLY, actor, "mobile");
            return null;
        });
    }

    /** B4 list shows live report status (a D1 sign-off appears on the web too). */
    @Bean
    PageOverlay b4Overlay() {
        return overlay("B4", payload -> {
            Map<String, String> status = jdbc.sql("select title, status from report")
                    .query((rs, i) -> Map.entry(rs.getString(1), rs.getString(2))).list().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            Json.patchArray(payload, "reports", "name", status, (o, st) -> o.put("status", st));
        });
    }

    // ── tiny factories so each domain reads as a list of rules ──
    interface Body {
        Object run(String actor, JsonNode payload);
    }

    static ActionHandler handler(String page, String action, Body body) {
        return new ActionHandler() {
            public String page() { return page; }
            public String action() { return action; }
            public Object handle(String actor, JsonNode payload) { return body.run(actor, payload); }
        };
    }

    static PageOverlay overlay(String page, java.util.function.Consumer<ObjectNode> fn) {
        return new PageOverlay() {
            public String page() { return page; }
            public void apply(ObjectNode payload) { fn.accept(payload); }
        };
    }
}
