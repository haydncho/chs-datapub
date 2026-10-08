package cn.ybdata.core.domain;

import cn.ybdata.core.page.PageScopeFilter;
import cn.ybdata.core.security.Actor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Per-viewer report state (报告签收按机构隔离 · 未批准不外发) for B4, D1, B1 and the hospital cockpit.
 *
 * <ul>
 *   <li><b>B4</b> {@code reports[]}: only released reports (and, for an institution, only those targeting it);
 *       each gets {@code id} and its {@code status} for <i>this</i> institution (sign / signed / check / old).
 *       {@code canSign} is true only for a 定点医疗机构 identity.</li>
 *   <li><b>D1</b> {@code report}: {@code id}, {@code status} (sign / signed / none = 未向本院发布),
 *       {@code meta} and {@code pendingLabel} from the report row, {@code signedAt}; {@code canSign};
 *       {@code receiptDone} (the 提醒函 of {@code alert.title} was answered by this institution);
 *       {@code me} labelled with the session user, organisation and login method.</li>
 *   <li><b>B1 · D1 · cockpit</b> (institution identities): {@code reportSignoff} =
 *       {@code {pending: n, pendingReports: [{id, name}], signedReports: [{id, name, signedAt}]}} — the
 *       「待签收」 figures other screens should show.</li>
 * </ul>
 */
@Configuration
public class ReportScope {

    /** D1 shows the current monthly report under a hospital-facing title */
    static final String CURRENT_MONTHLY = "R-2026-08";
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");

    private final ReportDomain reports;
    private final JdbcClient jdbc;

    public ReportScope(ReportDomain reports, JdbcClient jdbc) {
        this.reports = reports;
        this.jdbc = jdbc;
    }

    static boolean institution(Actor a) {
        return a.isHospital() && a.orgId() != null;
    }

    /** report id → its state as seen by this viewer; absent = not served to the viewer */
    record View(ReportDomain.Release rel, String status, String signedAt) {}

    Map<String, View> views(Actor a) {
        boolean inst = institution(a);
        Map<String, View> out = new HashMap<>();
        for (ReportDomain.Release rel : reports.releases()) {
            if (!rel.released()) continue;
            if (inst && !rel.targets(a.orgName())) continue;
            String st = rel.report().status();
            String at = null;
            if ("sign".equals(st) && inst) {
                var s = reports.signedAt(rel.report().id(), a.orgId());
                if (s.isPresent()) {
                    st = "signed";
                    at = s.get().atZoneSameInstant(PublishDomain.ZONE).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
                }
            }
            out.put(rel.report().id(), new View(rel, st, at));
        }
        return out;
    }

    static PageScopeFilter scope(String page, boolean institutionsOnly, BiConsumer<ObjectNode, Actor> fn) {
        return new PageScopeFilter() {
            public boolean appliesTo(String code, Actor actor) {
                return page.equals(code) && (!institutionsOnly || institution(actor));
            }

            public void apply(ObjectNode payload, Actor actor) {
                fn.accept(payload, actor);
            }
        };
    }

    @Bean
    PageScopeFilter b4ReportScope() {
        return scope("B4", false, (payload, actor) -> {
            Map<String, View> views = views(actor);
            applyB4(payload, actor, views);
            if (institution(actor)) payload.set("reportSignoff", summary(payload, views));
        });
    }

    @Bean
    PageScopeFilter d1ReportScope() {
        return scope("D1", false, (payload, actor) -> {
            Map<String, View> views = views(actor);
            applyD1(payload, actor, views);
            if (institution(actor)) payload.set("reportSignoff", summary(payload, views));
        });
    }

    @Bean
    PageScopeFilter b1ReportScope() {
        return scope("B1", true, (payload, actor) -> payload.set("reportSignoff", summary(payload, views(actor))));
    }

    @Bean
    PageScopeFilter cockpitReportScope() {
        return scope("cockpit", true, (payload, actor) -> payload.set("reportSignoff", summary(payload, views(actor))));
    }

    private void applyB4(ObjectNode payload, Actor actor, Map<String, View> views) {
        Map<String, View> byTitle = new HashMap<>();
        views.values().forEach(v -> byTitle.put(v.rel().report().title(), v));
        Set<String> known = new java.util.HashSet<>();
        reports.releases().forEach(r -> known.add(r.report().title()));
        ArrayNode kept = payload.arrayNode();
        for (JsonNode r : payload.path("reports")) {
            String name = r.path("name").asText();
            View v = byTitle.get(name);
            if (v == null) {
                if (!known.contains(name)) kept.add(r); // not a workflow report — served as seeded
                continue;
            }
            ObjectNode o = ((ObjectNode) r).deepCopy();
            o.put("id", v.rel().report().id());
            o.put("status", v.status());
            if (v.signedAt() != null) o.put("signedAt", v.signedAt());
            kept.add(o);
        }
        payload.set("reports", kept);
        payload.put("canSign", institution(actor));
        if (institution(actor) && actor.name() != null) {
            payload.put("signer", actor.name() + (actor.orgName() == null ? "" : " · " + actor.orgName()));
        }
    }

    private void applyD1(ObjectNode payload, Actor actor, Map<String, View> views) {
        boolean inst = institution(actor);
        payload.put("canSign", inst);
        if (payload.get("report") instanceof ObjectNode rep) {
            String id = rep.path("id").asText(CURRENT_MONTHLY);
            rep.put("id", id);
            View v = views.get(id);
            if (v == null) {
                rep.put("status", "none");
            } else {
                rep.put("status", v.status());
                ReportDomain.Report r = v.rel().report();
                rep.put("meta", r.kind() + " · " + r.version() + (r.published() == null ? "" : " · " + r.published().format(MD) + " 发布"));
                rep.put("pendingLabel", "待签收");
                if (v.signedAt() != null) rep.put("signedAt", v.signedAt());
            }
        }
        // 提醒函回执 done by this institution (alert rows are answered per institution)
        // the alert row (alert.id, e.g. AL-07) or, without an id, this institution's alert on the same metric
        String alertId = payload.path("alert").path("id").asText("");
        String metric = payload.path("alert").path("title").asText("").split(" ")[0];
        boolean done = inst && actor.orgName() != null && (!alertId.isEmpty() || !metric.isEmpty()) && jdbc.sql(
                        "select count(*) from alert where org_name = :o and status = 'ack' and "
                                + (alertId.isEmpty() ? "metric like :m" : "id = :m"))
                .param("o", actor.orgName()).param("m", alertId.isEmpty() ? metric + "%" : alertId).query(Integer.class).single() > 0;
        payload.put("receiptDone", done);
        if (payload.get("me") instanceof ObjectNode me && actor != null && actor.enforced()) {
            if (actor.name() != null) {
                me.put("name", actor.name());
                if (!actor.name().isEmpty()) me.put("initial", actor.name().substring(0, 1));
            }
            if (actor.orgName() != null) me.put("org", actor.orgName() + " · 本院具名");
            String method = actor.sessionId() == null ? null : jdbc.sql("select method from auth_session where id = :id")
                    .param("id", actor.sessionId()).query(String.class).optional().orElse(null);
            long signedCount = views.values().stream().filter(v -> "signed".equals(v.status())).count();
            for (JsonNode row : me.path("rows")) {
                if (!(row instanceof ObjectNode o)) continue;
                switch (o.path("k").asText()) {
                    case "登录方式" -> o.put("v", "cert".equals(method) ? "数字证书" : "sms".equals(method) ? "短信验证码" : "统一身份认证");
                    case "签收记录" -> o.put("v", signedCount + " 份");
                    default -> { }
                }
            }
        }
    }

    private ObjectNode summary(ObjectNode payload, Map<String, View> views) {
        ObjectNode s = payload.objectNode();
        ArrayNode pending = s.putArray("pendingReports");
        ArrayNode signed = s.putArray("signedReports");
        views.values().stream()
                .sorted(java.util.Comparator.comparing((View v) -> v.rel().report().id()))
                .forEach(v -> {
                    if ("sign".equals(v.status())) pending.addObject().put("id", v.rel().report().id()).put("name", v.rel().report().title());
                    if ("signed".equals(v.status())) signed.addObject().put("id", v.rel().report().id())
                            .put("name", v.rel().report().title()).put("signedAt", v.signedAt());
                });
        s.put("pending", pending.size());
        return s;
    }
}
