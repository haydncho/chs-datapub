package gov.ybj.chsdpub.audit;

import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.common.Texts;
import gov.ybj.chsdpub.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 审计日志（只增不改，保存 3 年）：登录、查阅、导出、打印、授权、越权尝试。
 * 导出 / 打印返回水印编号 WM-YYYYMMDD-NNNN，可在 A14 按编号溯源到人。
 */
@Service
public class AuditService {

    public static final String LOGIN = "登录";
    public static final String VIEW = "查阅";
    public static final String EXPORT = "导出";
    public static final String PRINT = "打印";
    public static final String GRANT = "授权";
    public static final String OVERREACH = "越权尝试";
    public static final String APPROVAL = "审批";

    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    private final JdbcTemplate jdbc;
    private final ZoneId zone;

    public AuditService(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.zone = ZoneId.of(props.zone());
    }

    public void record(AuthUser u, String type, String object, String result) {
        record(u == null ? "未知" : u.name(), u == null ? "—" : u.org(), type, object, result, null, currentIp());
    }

    public void record(String userName, String org, String type, String object, String result, String watermarkNo, String ip) {
        jdbc.update("insert into audit_log (user_name, org, type, object, ip, watermark_no, result) values (?,?,?,?,?,?,?)",
                userName, org, type, Texts.truncate(object, 128), ip, watermarkNo, result);
    }

    /** 越权尝试：在独立事务中写入（请求本身已被拒绝）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void overreach(AuthUser u, String target, String ip) {
        record(u.name(), u.org(), OVERREACH, "以「" + u.roleLabel() + "」身份访问 " + target, "已拦截", null, ip);
    }

    /** 原子取当日水印编号。 */
    public String nextWatermark() {
        LocalDate today = LocalDate.now(zone);
        Integer n = jdbc.queryForObject("""
                insert into watermark_counter (day, next_val) values (?, 1002)
                on conflict (day) do update set next_val = watermark_counter.next_val + 1
                returning next_val - 1""", Integer.class, today);
        return "WM-" + today.format(YMD) + "-" + String.format("%04d", n == null ? 1001 : n);
    }

    public static String currentIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return null;
        return clientIp(attrs.getRequest());
    }

    /** 专网部署：仅采信本机反向代理（Nginx）写入的 X-Real-IP。 */
    public static String clientIp(HttpServletRequest req) {
        String remote = req.getRemoteAddr();
        boolean local = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || remote.startsWith("172.") || remote.startsWith("10.");
        String xr = req.getHeader("X-Real-IP");
        return local && xr != null && !xr.isBlank() ? xr.trim() : remote;
    }
}
