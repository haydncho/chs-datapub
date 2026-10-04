package gov.ybj.chsdpub.regional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ybj.chsdpub.audit.AuditService;
import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.auth.CurrentUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C3 外部监督只读席位：人大代表 / 政协委员在有效期内只读查阅发布会材料、限时回看录像。
 * 有效期由服务端校验——到期后本前缀下所有接口返回 403（code = SEAT_EXPIRED）并写审计；
 * 不提供下载、打印、导出（无对应接口，导出范围也不含 C3）。
 */
@RestController
@RequestMapping("/api/v1/supervision")
public class SupervisionController {

    public static final String SEAT_EXPIRED = "SEAT_EXPIRED";
    private static final DateTimeFormatter YMDHM = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final TypeReference<List<Object>> LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ObjectMapper om;
    private final ZoneId zone;

    public SupervisionController(JdbcTemplate jdbc, AuditService audit, ObjectMapper om, AppProperties props) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.om = om;
        this.zone = ZoneId.of(props.zone());
    }

    record Seat(String event, OffsetDateTime from, OffsetDateTime until) {}

    /** 校验席位有效期：未开通 / 未生效 / 已到期一律 403。 */
    private Seat seat(AuthUser u) {
        List<Seat> r = jdbc.query("select event, valid_from, valid_until from rg_seat where user_id = ?",
                (rs, i) -> new Seat(rs.getString(1), rs.getObject(2, OffsetDateTime.class), rs.getObject(3, OffsetDateTime.class)),
                u.userId());
        if (r.isEmpty()) throw ApiException.forbidden("当前账号未开通只读席位");
        Seat s = r.get(0);
        OffsetDateTime now = OffsetDateTime.now(zone);
        if (now.isBefore(s.from())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SEAT_NOT_STARTED", "只读席位将于 " + fmt(s.from()) + " 生效");
        }
        if (!now.isBefore(s.until())) {
            audit.record(u, AuditService.VIEW, "只读席位到期后访问 · " + s.event(), "已拦截");
            throw new ApiException(HttpStatus.FORBIDDEN, SEAT_EXPIRED, "只读席位已于 " + fmt(s.until()) + " 到期自动失效,不能继续查阅");
        }
        return s;
    }

    private String fmt(OffsetDateTime t) {
        return t.atZoneSameInstant(zone).format(YMDHM);
    }

    @GetMapping("/seat")
    public Map<String, Object> seat() {
        AuthUser u = CurrentUser.get();
        Seat s = seat(u);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("event", s.event());
        out.put("validFrom", fmt(s.from()));
        out.put("validUntil", fmt(s.until()));
        out.put("validUntilIso", s.until().atZoneSameInstant(zone).toOffsetDateTime().toString());
        // 剩余秒数以服务端时钟为准，前端据此倒计时（避免终端时钟偏差）
        out.put("remainingSeconds", Math.max(0, Duration.between(OffsetDateTime.now(zone), s.until()).toSeconds()));
        out.put("materials", jdbc.query("select id, name, format from rg_material order by sort, id",
                (rs, i) -> Map.of("id", rs.getLong(1), "name", rs.getString(2), "format", rs.getString(3))));
        out.put("recording", jdbc.queryForMap("select id, title, duration_s as \"durationS\", watched_s as \"watchedS\" from rg_recording order by id limit 1"));
        return out;
    }

    @GetMapping("/materials/{id}")
    public Map<String, Object> material(@PathVariable long id) {
        AuthUser u = CurrentUser.get();
        seat(u);
        List<Map<String, Object>> r = jdbc.query("select id, name, format, heading, kpis::text, body::text from rg_material where id = ?",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong(1));
                    m.put("name", rs.getString(2));
                    m.put("format", rs.getString(3));
                    m.put("heading", rs.getString(4));
                    m.put("kpis", parse(rs.getString(5)));
                    m.put("body", parse(rs.getString(6)));
                    return m;
                }, id);
        if (r.isEmpty()) throw ApiException.notFound("材料不存在");
        audit.record(u, AuditService.VIEW, "发布会材料 · " + r.get(0).get("name"), "只读预览");
        return r.get(0);
    }

    public record ProgressReq(int watchedS) {}

    /** 录像回看进度（只记已观看位置，不提供下载）。 */
    @PostMapping("/recording/progress")
    public Map<String, Object> progress(@RequestBody ProgressReq req) {
        seat(CurrentUser.get());
        jdbc.update("update rg_recording set watched_s = greatest(0, least(duration_s, ?))", req.watchedS());
        return jdbc.queryForMap("select id, title, duration_s as \"durationS\", watched_s as \"watchedS\" from rg_recording order by id limit 1");
    }

    private List<Object> parse(String json) {
        try {
            return om.readValue(json, LIST);
        } catch (Exception e) {
            return List.of();
        }
    }
}
