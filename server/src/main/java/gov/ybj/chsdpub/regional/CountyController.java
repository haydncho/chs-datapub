package gov.ybj.chsdpub.regional;

import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.auth.CurrentUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.engine.EngineClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C1 县区医保视图：县区 = 会话身份机构（user_identity.org ↔ rg_county.bureau_org）。
 * 后端裁剪：机构明细只查本县区（SQL 条件），其他县区只给汇总行；排名与指标判级由引擎计算。
 */
@RestController
@RequestMapping("/api/v1/county")
public class CountyController {

    /** 清单质控率低于该值标橙。 */
    static final double QC_THRESHOLD = 95.0;
    static final String RANK_BASIS = "县区医保综合考核得分";

    private final JdbcTemplate jdbc;
    private final EngineClient engine;

    public CountyController(JdbcTemplate jdbc, EngineClient engine) {
        this.jdbc = jdbc;
        this.engine = engine;
    }

    record Own(long id, String name, String period) {}

    private Own own(AuthUser u) {
        List<Own> r = jdbc.query("select id, name, period from rg_county where bureau_org = ?",
                (rs, i) -> new Own(rs.getLong(1), rs.getString(2), rs.getString(3)), u.org());
        if (r.isEmpty()) throw ApiException.forbidden("当前身份未关联县区,无法查看县区视图");
        return r.get(0);
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Own c = own(CurrentUser.get());
        // 只取本县区机构：其他县区的机构明细不出库、不下发
        List<Map<String, Object>> inst = jdbc.query("""
                select name, level, cases, avg_diff, list_qc_pct, cost_pctl from rg_county_institution
                where county_id = ? order by sort, id""",
                (rs, i) -> Map.<String, Object>of("name", rs.getString(1), "level", rs.getString(2), "cases", rs.getInt(3),
                        "avgDiff", rs.getBigDecimal(4), "listQcPct", rs.getBigDecimal(5), "costPctl", rs.getInt(6)), c.id());
        List<Map<String, Object>> counties = jdbc.query("select id, name, avg_diff, list_qc_pct, score from rg_county order by sort",
                (rs, i) -> Map.<String, Object>of("name", rs.getString(2), "avgDiff", rs.getBigDecimal(3), "listQcPct", rs.getBigDecimal(4),
                        "score", rs.getBigDecimal(5), "self", rs.getLong(1) == c.id()));
        List<String> alliancePeriod = jdbc.queryForList("select distinct period from rg_alliance_indicator where county_id = ?", String.class, c.id());
        List<Map<String, Object>> indicators = jdbc.query("""
                select name, value, unit, signed, direction, warn_line, alarm_line from rg_alliance_indicator
                where county_id = ? order by sort, id""",
                (rs, i) -> Map.<String, Object>of("name", rs.getString(1), "value", rs.getBigDecimal(2), "unit", rs.getString(3),
                        "signed", rs.getBoolean(4), "direction", rs.getString(5), "warn", rs.getBigDecimal(6), "alarm", rs.getBigDecimal(7)), c.id());

        Map<String, Object> calc = engine.post("/v1/regional/county",
                Map.of("institutions", inst, "counties", counties, "indicators", indicators, "qcThreshold", QC_THRESHOLD));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("county", c.name());
        out.put("period", c.period());
        out.put("alliancePeriod", alliancePeriod.isEmpty() ? null : alliancePeriod.get(0));
        out.put("qcThreshold", QC_THRESHOLD);
        out.put("rankBasis", RANK_BASIS);
        out.put("institutions", calc.get("institutions"));
        out.put("counties", calc.get("counties"));
        out.put("indicators", calc.get("indicators"));
        out.put("statusCounts", calc.get("statusCounts"));
        return out;
    }
}
