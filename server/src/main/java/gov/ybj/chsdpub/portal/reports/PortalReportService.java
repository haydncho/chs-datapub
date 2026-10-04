package gov.ybj.chsdpub.portal.reports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ybj.chsdpub.audit.AuditService;
import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.config.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 定向发布报告（B4 报告中心与 D1 移动端共用）：只返回当前身份所属机构的报告；签收持久化并写审计「查阅」。
 * 他院报告一律按「不存在」处理（不泄露存在性）。
 */
@Service
public class PortalReportService {

    public static final String SIGN = "SIGN", CHECK = "CHECK", SIGNED = "SIGNED", OLD = "OLD";
    public static final List<String> KINDS = List.of("月度报告", "专题报告", "体检报告");

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter MDHM = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ObjectMapper om;
    private final ZoneId zone;

    public PortalReportService(JdbcTemplate jdbc, AuditService audit, ObjectMapper om, AppProperties props) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.om = om;
        this.zone = ZoneId.of(props.zone());
    }

    public record Report(long id, String title, String kind, String published, int pages, String status, String signedAt,
                         String signedBy) {}

    public record ReportDetail(long id, String title, String kind, String published, int pages, String status, String signedAt,
                               String signedBy, String header, String watermark, String wmNo, JsonNode body) {}

    private static final String COLS = "id, title, kind, published_on, pages, status, signed_at, signed_by";

    private Report map(ResultSet rs) throws SQLException {
        return new Report(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getObject(4, LocalDate.class).format(MD), rs.getInt(5),
                rs.getString(6), fmt(rs.getObject(7, OffsetDateTime.class)), rs.getString(8));
    }

    String fmt(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(zone).format(MDHM);
    }

    public List<Report> list(String org, String kind) {
        if (kind != null && !kind.isBlank() && !"全部".equals(kind)) {
            if (!KINDS.contains(kind)) throw ApiException.validation("报告类别不正确");
            return jdbc.query("select " + COLS + " from pr_report where org = ? and kind = ? order by sort", (rs, i) -> map(rs), org, kind);
        }
        return jdbc.query("select " + COLS + " from pr_report where org = ? order by sort", (rs, i) -> map(rs), org);
    }

    public Report one(String org, long id) {
        return jdbc.query("select " + COLS + " from pr_report where org = ? and id = ?", (rs, i) -> map(rs), org, id).stream()
                .findFirst().orElseThrow(() -> ApiException.notFound("报告不存在"));
    }

    public ReportDetail detail(AuthUser u, long id) {
        Report r = one(u.org(), id);
        Map<String, Object> x = jdbc.queryForMap("select wm_no, body::text body from pr_report where id = ?", id);
        String wm = (String) x.get("wm_no");
        JsonNode body;
        try {
            body = om.readTree((String) x.get("body"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new ReportDetail(r.id(), r.title(), r.kind(), r.published(), r.pages(), r.status(), r.signedAt(), r.signedBy(),
                "示例市医疗保障局 医保数据工作组 · 定向发布 · 仅限" + u.org(), u.name() + " " + u.org() + " " + wm, wm, body);
    }

    /** 签收：仅「待签收」可签收；签收记录供医保局发布工作流「签收查阅」节点读取（pr_report.signed_at）。 */
    @Transactional
    public Report sign(AuthUser u, long id) {
        Report r = one(u.org(), id);
        if (!SIGN.equals(r.status())) {
            throw ApiException.conflict(switch (r.status()) {
                case CHECK -> "核对稿请在「意见与机构核对」完成核对";
                case OLD -> "原版本只读保留,不需签收";
                default -> "该报告已签收";
            });
        }
        int n = jdbc.update("update pr_report set status = 'SIGNED', signed_at = now(), signed_by = ? where id = ? and org = ? and status = 'SIGN'",
                u.name(), id, u.org());
        if (n == 0) throw ApiException.conflict("该报告已签收");
        // 定向发布生成的报告:签收数回写发布版本(A8「签收查阅」与更正与撤回页读取)
        jdbc.update("update pub_release set signed = signed + 1 where id = (select release_id from pr_report where id = ?)", id);
        String wm = jdbc.queryForObject("select wm_no from pr_report where id = ?", String.class, id);
        audit.record(u.name(), u.org(), AuditService.VIEW, "签收报告《" + r.title() + "》", "已签收", wm, AuditService.currentIp());
        return one(u.org(), id);
    }
}
