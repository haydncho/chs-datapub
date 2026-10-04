package gov.ybj.chsdpub.audit;

import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.config.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 审计日志（A14）：只读；按操作类型筛选；按水印编号溯源到人、时间、文件。 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    public static final List<String> TYPES = List.of("全部", AuditService.LOGIN, AuditService.VIEW, AuditService.EXPORT,
            AuditService.PRINT, AuditService.GRANT, AuditService.APPROVAL, AuditService.OVERREACH);
    private static final DateTimeFormatter MDHM = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbc;
    private final ZoneId zone;

    public AuditController(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.zone = ZoneId.of(props.zone());
    }

    public record Log(long id, String at, String user, String org, String type, String object, String ip, String watermarkNo, String result) {}

    @GetMapping("/logs")
    public Map<String, Object> logs(@RequestParam(defaultValue = "全部") String type, @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int size) {
        if (!TYPES.contains(type)) throw ApiException.validation("操作类型无效");
        int s = Math.max(1, Math.min(size, 100));
        int p = Math.max(1, page);
        String where = "全部".equals(type) ? "" : " where type = ?";
        List<Object> args = new ArrayList<>();
        if (!where.isEmpty()) args.add(type);
        Long total = jdbc.queryForObject("select count(*) from audit_log" + where, Long.class, args.toArray());
        args.add(s);
        args.add((p - 1) * s);
        List<Log> rows = jdbc.query("""
                select id, at, user_name, org, type, object, ip, watermark_no, result from audit_log""" + where
                        + " order by at desc, id desc limit ? offset ?",
                (rs, i) -> new Log(rs.getLong(1), rs.getObject(2, OffsetDateTime.class).atZoneSameInstant(zone).format(MDHM),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getString(9)), args.toArray());
        LocalDate today = LocalDate.now(zone);
        Map<String, Object> day = jdbc.queryForMap("""
                select count(*) n, count(*) filter (where type = ?) o from audit_log
                where at >= ?::date::timestamp at time zone ? and at < (?::date + 1)::timestamp at time zone ?""",
                AuditService.OVERREACH, today.toString(), zone.getId(), today.toString(), zone.getId());
        return Map.of("types", TYPES, "rows", rows, "total", total == null ? 0 : total, "page", p, "size", s,
                "todayCount", day.get("n"), "todayOverreach", day.get("o"));
    }

    public record Trace(String user, String org, String at, String type, String object, String ip) {}

    @GetMapping("/trace")
    public Trace trace(@RequestParam String wm) {
        List<Trace> r = jdbc.query("""
                select user_name, org, at, type, object, ip from audit_log where watermark_no = ? order by at limit 1""",
                (rs, i) -> new Trace(rs.getString(1), rs.getString(2), rs.getObject(3, OffsetDateTime.class).atZoneSameInstant(zone).format(FULL),
                        rs.getString(4), rs.getString(5), rs.getString(6)), wm.trim());
        if (r.isEmpty()) throw ApiException.notFound("未找到该水印编号");
        return r.get(0);
    }
}
