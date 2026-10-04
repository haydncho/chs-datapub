package gov.ybj.chsdpub.portal.reports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.auth.CurrentUser;
import gov.ybj.chsdpub.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * D1 移动端摘要（政务 APP 内嵌）：待签收报告（与 B4 同一数据）、本院预警（读第二批 alert_trigger 中已发给本院的记录）、
 * 本院 4 项 KPI、重点病组、核对引导。移动端仅查看与签收；意见、核对、回执、导出在 PC 端。
 */
@RestController
@RequestMapping("/api/v1/portal/mobile")
public class PortalMobileController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper om;
    private final PortalReportService reports;
    private final PortalOpinionService opinions;

    public PortalMobileController(JdbcTemplate jdbc, ObjectMapper om, PortalReportService reports, PortalOpinionService opinions) {
        this.jdbc = jdbc;
        this.om = om;
        this.reports = reports;
        this.opinions = opinions;
    }

    public record AlertCard(long id, String rule, String summary) {}

    public record ReportRef(long id, String title, String signedAt) {}

    private JsonNode json(String s) {
        try {
            return om.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        AuthUser u = CurrentUser.get();
        List<PortalReportService.Report> all = reports.list(u.org(), null);
        Map<String, Object> m = new HashMap<>();
        m.put("org", u.org());
        all.stream().filter(r -> PortalReportService.SIGN.equals(r.status())).findFirst()
                .ifPresent(r -> m.put("pending", new ReportRef(r.id(), r.title(), null)));
        jdbc.query("""
                        select id, title, signed_at from pr_report where org = ? and status = 'SIGNED' and signed_at is not null
                        order by signed_at desc limit 1""",
                        (rs, i) -> new ReportRef(rs.getLong(1), rs.getString(2), reports.fmt(rs.getObject(3, java.time.OffsetDateTime.class))), u.org())
                .stream().findFirst().ifPresent(r -> m.put("lastSigned", r));
        m.put("alerts", jdbc.query("""
                select t.id, t.rule_name, coalesce(n.summary, t.value) from alert_trigger t left join pr_alert_note n on n.trigger_id = t.id
                where t.org = ? and t.status in ('SENT', 'RCPT', 'FIX') order by t.sort""",
                (rs, i) -> new AlertCard(rs.getLong(1), rs.getString(2), rs.getString(3)), u.org()));
        jdbc.queryForList("select period, kpis::text kpis, groups::text groups from pr_org_summary where org = ?", u.org()).stream()
                .findFirst().ifPresent(s -> {
                    m.put("period", s.get("period"));
                    m.put("kpis", json((String) s.get("kpis")));
                    m.put("groups", json((String) s.get("groups")));
                });
        PortalOpinionService.CheckRound r = opinions.round(u.org());
        if (r != null) {
            long open = r.items().stream().filter(x -> "OPEN".equals(x.state())).count();
            m.put("check", Map.of("title", r.title(), "deadlineLabel", r.deadlineLabel(), "closed", r.closed(), "openItems", open));
        }
        return m;
    }

    public record AlertDetail(long id, String rule, String title, String period, String value, Integer percentile, Integer line,
                              String narrative, String letterNo, String status, String group) {}

    @GetMapping("/alerts/{id}")
    public AlertDetail alert(@PathVariable long id) {
        String org = CurrentUser.get().org();
        return jdbc.query("""
                select t.id, t.rule_name, coalesce(n.title, t.rule_name), t.period, coalesce(n.value, t.value), n.percentile, n.line,
                       n.narrative, t.letter_seq, t.status, t.drg_group
                from alert_trigger t left join pr_alert_note n on n.trigger_id = t.id
                where t.id = ? and t.org = ? and t.status in ('SENT', 'RCPT', 'FIX')""", (rs, i) -> new AlertDetail(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), (Integer) rs.getObject(6),
                (Integer) rs.getObject(7), rs.getString(8), "示医保提〔2026〕" + rs.getInt(9) + "号", rs.getString(10), rs.getString(11)),
                id, org).stream().findFirst().orElseThrow(() -> ApiException.notFound("预警不存在"));
    }
}
