package gov.ybj.chsdpub.holo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ybj.chsdpub.audit.AuditService;
import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.common.Roles;
import gov.ybj.chsdpub.engine.EngineClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 全息图：A2 医保数据公开全息图（医保局，分析监测区）与 B1 本院全息图（机构门户，发布区）。
 * 半径、色阶、关键少数、图层外环、口径换算、占比、分位着色等派生数字一律来自分析引擎；服务端只取数与按身份裁剪。
 */
@Service
public class HoloService {

    private static final TypeReference<List<Number>> NUMS = new TypeReference<>() {};
    /** 月 / 季 / 年 → 期次文字。 */
    static final Map<String, String> PERIOD_LABEL = Map.of("月", "2026年8月", "季", "2026年第三季度", "年", "2025年度");

    private final JdbcTemplate jdbc;
    private final EngineClient engine;
    private final ObjectMapper om;
    private final AuditService audit;

    public HoloService(JdbcTemplate jdbc, EngineClient engine, ObjectMapper om, AuditService audit) {
        this.jdbc = jdbc;
        this.engine = engine;
        this.om = om;
        this.audit = audit;
    }

    private static String period(String p) {
        if (!PERIOD_LABEL.containsKey(p)) throw ApiException.validation("口径只能是 月 / 季 / 年");
        return p;
    }

    private List<Number> nums(String json) {
        try {
            return om.readValue(json, NUMS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ================================================================ A2

    private List<Map<String, Object>> groups() {
        return jdbc.query("""
                select code, name, cases, avg_diff, avg_cost, time_index, audit_deduct_wan, upcode_cases
                from holo_drg order by sort""", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", rs.getString(1));
            m.put("name", rs.getString(2));
            m.put("cases", rs.getInt(3));
            m.put("avgDiff", rs.getInt(4));
            m.put("avgCost", rs.getInt(5));
            m.put("timeIndex", rs.getBigDecimal(6));
            m.put("auditDeductWan", rs.getBigDecimal(7));
            m.put("upcodeCases", rs.getInt(8));
            return m;
        });
    }

    private Map<String, Object> publication() {
        List<String> audiences = jdbc.queryForList("select name from holo_pub_audience order by sort", String.class);
        Map<Integer, Map<String, Object>> rows = new LinkedHashMap<>();
        jdbc.query("select sort, name, grp from holo_pub_indicator order by sort", rs -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", rs.getString(2));
            r.put("grp", rs.getString(3));
            r.put("cells", new ArrayList<Map<String, Object>>());
            rows.put(rs.getInt(1), r);
        });
        jdbc.query("select indicator_sort, state, value from holo_pub_cell order by indicator_sort, audience_sort", rs -> {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("state", rs.getString(2));
            c.put("value", rs.getObject(3));
            @SuppressWarnings("unchecked") List<Map<String, Object>> cells = (List<Map<String, Object>>) rows.get(rs.getInt(1)).get("cells");
            cells.add(c);
        });
        Map<String, Object> st = jdbc.queryForMap("select target, tier, latest, sign_pct, read_pct, opinions, replied, overdue from holo_pub_status where id = 1");
        Map<String, Object> eng = engine.post("/v1/holo/publication", Map.of("rows", rows.values(),
                "status", Map.of("opinions", st.get("opinions"), "replied", st.get("replied"))));
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("target", st.get("target"));
        status.put("tier", st.get("tier"));
        status.put("latest", st.get("latest"));
        status.put("signPct", st.get("sign_pct"));
        status.put("readPct", st.get("read_pct"));
        status.put("opinions", st.get("opinions"));
        status.put("replyPct", eng.get("replyPct"));
        status.put("overdue", st.get("overdue"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("audiences", audiences);
        out.put("rows", eng.get("rows"));
        out.put("counts", eng.get("counts"));
        out.put("status", status);
        return out;
    }

    public record Shortcut(String kind, String title, String sub) {}

    /** A2 概览：气泡全景（引擎）、公开状态矩阵与状态卡（引擎计数）、快捷专区。 */
    public Map<String, Object> overview(String p) {
        String period = period(p);
        Map<String, Object> pano = engine.post("/v1/holo/panorama", Map.of("period", period, "groups", groups()));
        String keySub = "关键少数 " + pano.get("keyCount") + " 个 · 逆差 " + pano.get("keyDeficitWan") + "万";
        List<Shortcut> shortcuts = jdbc.query("select kind, title, sub from holo_shortcut order by sort",
                (rs, i) -> new Shortcut(rs.getString(1), rs.getString(2), rs.getString(3) == null ? keySub : rs.getString(3)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period);
        out.put("periodLabel", PERIOD_LABEL.get(period));
        out.put("panorama", pano);
        out.put("publication", publication());
        out.put("shortcuts", shortcuts);
        return out;
    }

    /** A2 选中病组详情：费用结构、同级分位、行为归因、标杆差距。 */
    public Map<String, Object> group(String code, String p) {
        String period = period(p);
        List<Map<String, Object>> r = jdbc.queryForList("""
                select code, name, cases, avg_diff, avg_cost, cost_mix::text cm, bench_mix::text bm, peer_pct
                from holo_drg where code = ?""", code);
        if (r.isEmpty()) throw ApiException.notFound("病组 " + code + " 不存在");
        Map<String, Object> d = r.get(0);
        List<Map<String, Object>> beh = jdbc.query("select name, rate_pct, multiplier from holo_drg_behavior where code = ? order by sort",
                (rs, i) -> Map.of("name", rs.getString(1), "ratePct", rs.getBigDecimal(2), "multiplier", rs.getBigDecimal(3)), code);
        List<Map<String, Object>> gaps = jdbc.query("select metric, value, bench, unit, kind from holo_drg_gap where code = ? order by sort",
                (rs, i) -> Map.of("metric", rs.getString(1), "value", rs.getBigDecimal(2), "bench", rs.getBigDecimal(3),
                        "unit", rs.getString(4), "kind", rs.getString(5)), code);
        Map<String, Object> g = Map.of("code", d.get("code"), "name", d.get("name"), "cases", d.get("cases"),
                "avgDiff", d.get("avg_diff"), "avgCost", d.get("avg_cost"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("period", period);
        body.put("group", g);
        body.put("costMix", nums((String) d.get("cm")));
        body.put("benchMix", nums((String) d.get("bm")));
        body.put("peerPct", d.get("peer_pct"));
        body.put("behaviors", beh);
        body.put("gaps", gaps);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", d.get("code"));
        out.put("name", d.get("name"));
        out.put("scope", "示例市 · 全部机构 · " + PERIOD_LABEL.get(period));
        out.putAll(engine.post("/v1/holo/group-detail", body));
        return out;
    }

    /** A2 区域外图层：流向（引擎占比 / 线宽）、汇总；就医地机构排名仅医保局可见，不进入发布区。 */
    public Map<String, Object> offsite(AuthUser u) {
        List<Map<String, Object>> flows = jdbc.query("select region, scope, amount_wan from holo_flow order by sort",
                (rs, i) -> Map.of("region", rs.getString(1), "scope", rs.getString(2), "amountWan", rs.getBigDecimal(3)));
        Map<String, Object> eng = engine.post("/v1/holo/flows", Map.of("flows", flows));
        Map<String, Object> s = jdbc.queryForMap("select fund_share, visits, source from holo_offsite where id = 1");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", s.get("source"));
        out.put("totalText", eng.get("totalText"));
        out.put("fundSharePct", s.get("fund_share"));
        out.put("visits", s.get("visits"));
        out.put("flows", eng.get("flows"));
        out.put("levels", jdbc.query("select name, pct from holo_offsite_level order by sort",
                (rs, i) -> Map.of("name", rs.getString(1), "pct", rs.getInt(2))));
        out.put("diseases", jdbc.query("select name, pct from holo_offsite_disease order by sort",
                (rs, i) -> Map.of("name", rs.getString(1), "pct", rs.getBigDecimal(2))));
        if (Set.of(Roles.CONVENER, Roles.ADMIN_GROUP).contains(u.role())) {
            out.put("ranking", jdbc.query("select facility, visits from holo_offsite_rank order by sort",
                    (rs, i) -> Map.of("facility", rs.getString(1), "visits", rs.getInt(2))));
        }
        return out;
    }

    // ================================================================ B1

    /**
     * B1 本院全息图：只返回本院病组（病例 < 30 并入“其他”）与全市同级同组均值（仅病组编码与均值，无任何机构字段）。
     * 机构由会话身份确定，不接受前端传入。
     */
    public Map<String, Object> hospital(AuthUser u) {
        List<Map<String, Object>> orgs = jdbc.queryForList("""
                select code, name, tier, peer_count, period, tier_mode, pending_sign, alert, ledger_wan, drg_pay_wan, cases, diff_pct
                from holo_org where name = ?""", u.org());
        if (orgs.isEmpty()) throw ApiException.notFound("本院本期暂无全息数据");
        Map<String, Object> o = orgs.get(0);
        String org = (String) o.get("code");
        List<Map<String, Object>> own = jdbc.query("""
                select d.code, d.name, o.cases, o.avg_diff, d.avg_cost from holo_org_drg o join holo_drg d on d.code = o.code
                where o.org_code = ? order by d.sort""", (rs, i) -> Map.of("code", rs.getString(1), "name", rs.getString(2),
                "cases", rs.getInt(3), "avgDiff", rs.getInt(4), "avgCost", rs.getInt(5)), org);
        List<Map<String, Object>> peers = jdbc.query("""
                select p.code, p.avg_cases, p.avg_diff, d.avg_cost from holo_peer_drg p join holo_drg d on d.code = p.code
                where p.tier = ? and p.code in (select code from holo_org_drg where org_code = ?) order by d.sort""",
                (rs, i) -> Map.of("code", rs.getString(1), "avgCases", rs.getBigDecimal(2), "avgDiff", rs.getInt(3), "avgCost", rs.getInt(4)),
                o.get("tier"), org);
        List<Map<String, Object>> ind = jdbc.query("select name, value, unit, pct, higher_worse from holo_org_indicator where org_code = ? order by sort",
                (rs, i) -> Map.of("name", rs.getString(1), "value", rs.getString(2), "unit", rs.getString(3), "pct", rs.getInt(4),
                        "higherWorse", rs.getBoolean(5)), org);
        List<Map<String, Object>> trend = jdbc.query("select month, avg_diff from holo_org_trend where org_code = ? order by sort",
                (rs, i) -> Map.of("month", rs.getString(1), "avgDiff", rs.getInt(2)), org);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("own", own);
        body.put("peers", peers);
        body.put("peerCount", o.get("peer_count"));
        body.put("indicators", ind);
        body.put("ledger", Map.of("ledgerWan", o.get("ledger_wan"), "drgPayWan", o.get("drg_pay_wan"), "cases", o.get("cases"),
                "diffPct", o.get("diff_pct")));
        body.put("trend", trend);
        Map<String, Object> eng = engine.post("/v1/holo/hospital", body);

        Map<String, Object> head = new LinkedHashMap<>();
        head.put("name", o.get("name"));
        head.put("tier", o.get("tier"));
        head.put("peerCount", o.get("peer_count"));
        head.put("period", o.get("period"));
        head.put("tierMode", o.get("tier_mode"));
        head.put("pendingSign", o.get("pending_sign"));
        head.put("alert", o.get("alert"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("org", head);
        out.putAll(eng);
        audit.record(u, AuditService.VIEW, "本院全息图 " + o.get("period"), "成功");
        return out;
    }
}
