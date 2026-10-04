package gov.ybj.chsdpub.regional;

import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.engine.EngineClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * C2 省级与区域外汇总：只看各统筹区发布情况与监测汇总层。
 * 接口不含任何机构级字段（数据源 rg_region 本身即统筹区汇总表）；排序口径、逾期判定与 KPI 由引擎计算。
 */
@RestController
@RequestMapping("/api/v1/province")
public class ProvinceController {

    static final Set<String> SORT_KEYS = Set.of("name", "period", "date", "signPct", "readPct", "replyPct", "balancePct",
            "coveragePct", "avgDiff");

    private final JdbcTemplate jdbc;
    private final EngineClient engine;

    public ProvinceController(JdbcTemplate jdbc, EngineClient engine) {
        this.jdbc = jdbc;
        this.engine = engine;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(defaultValue = "signPct") String sort,
                                       @RequestParam(defaultValue = "desc") String dir) {
        if (!SORT_KEYS.contains(sort)) throw ApiException.validation("排序列无效");
        if (!"asc".equals(dir) && !"desc".equals(dir)) throw ApiException.validation("排序方向无效");
        LocalDate asOf = jdbc.queryForObject("select as_of from rg_province_meta where id = 1", LocalDate.class);
        List<Map<String, Object>> regions = jdbc.query("""
                select name, is_self, last_period, last_date, next_due, sign_pct, prev_sign_pct, read_pct, reply_pct,
                       balance_pct, fund_income, coverage_pct, avg_diff
                from rg_region order by sort, id""", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", rs.getString(1));
            m.put("self", rs.getBoolean(2));
            m.put("lastPeriod", rs.getString(3));
            m.put("lastDate", rs.getObject(4, LocalDate.class).toString());
            m.put("nextDue", rs.getObject(5, LocalDate.class).toString());
            m.put("signPct", rs.getBigDecimal(6));
            m.put("prevSignPct", rs.getBigDecimal(7));
            m.put("readPct", rs.getBigDecimal(8));
            m.put("replyPct", rs.getBigDecimal(9));
            m.put("balancePct", rs.getBigDecimal(10));
            m.put("fundIncome", rs.getBigDecimal(11));
            m.put("coveragePct", rs.getBigDecimal(12));
            m.put("avgDiff", rs.getBigDecimal(13));
            return m;
        });
        Map<String, Object> calc = engine.post("/v1/regional/province",
                Map.of("asOf", String.valueOf(asOf), "regions", regions, "sort", sort, "dir", dir));
        List<Map<String, Object>> insights = jdbc.query("select title, body from rg_province_insight order by sort, id",
                (rs, i) -> Map.<String, Object>of("title", rs.getString(1), "body", rs.getString(2)));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("asOf", String.valueOf(asOf));
        out.put("sort", calc.get("sort"));
        out.put("dir", calc.get("dir"));
        out.put("kpis", calc.get("kpis"));
        out.put("rows", calc.get("rows"));
        out.put("insights", insights);
        return out;
    }
}
