package gov.ybj.chsdpub.portal.analysis;

import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.engine.EngineClient;
import gov.ybj.chsdpub.policy.DisplayPolicyController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 机构门户分析（B2 病组下钻与专题 / B3 对标与PK / B7 区域外数据）。
 * <p>
 * 所有查询按当前会话机构（{@code CurrentUser.get().org()}）过滤，只返回本院数据：
 * <ul>
 *   <li>B2 小样本病组（病例 &lt; 30，由引擎判定）只下发编码与名称，数值并入「其他」；</li>
 *   <li>B3 按第二批 {@code benchmark_tier} 的当前档位裁剪：匿名分位只下发本院值与分位，
 *       匿名编号只下发「三级医院A–E」与数值，只有具名对比与排行档才下发他院名称；</li>
 *   <li>B7 只有按地区、等级、病种的汇总，库中不存、接口不返回就医地机构明细。</li>
 * </ul>
 * 分位、排名、匿名编号、小样本合并、偏差与差距由引擎计算；引擎不可用时 503。
 */
@Service
public class PortalAnalysisService {

    static final String PERIOD = "2026-08";
    static final String PERIOD_LABEL = "2026年8月";

    private final JdbcTemplate jdbc;
    private final EngineClient engine;

    public PortalAnalysisService(JdbcTemplate jdbc, EngineClient engine) {
        this.jdbc = jdbc;
        this.engine = engine;
    }

    // ================================================================ 同级组

    public record Peer(String group, List<String> orgs) {}

    /** 本院所在同级组；未纳入对标的机构 404。 */
    Peer peer(String org) {
        List<String> g = jdbc.queryForList("select peer_group from pa_peer_org where org = ?", String.class, org);
        if (g.isEmpty()) throw ApiException.notFound("本院暂未纳入同级对标");
        List<String> orgs = jdbc.queryForList("select org from pa_peer_org where peer_group = ? order by sort", String.class, g.get(0));
        return new Peer(g.get(0), orgs);
    }

    // ================================================================ B2 病组下钻与专题

    public record GroupRef(String code, String name) {}

    public record Other(int cases, int avgDiff) {}

    public record Topic(String title, String status, String page, String linkLabel) {}

    public record GroupIndex(String periodLabel, String peerGroup, int peerCount, List<GroupRef> groups, List<GroupRef> small,
                             Other other, List<Topic> topics) {}

    private record GroupRow(String code, String name, int cases, int avgCost, int benchCost, int avgDiff, int costPct,
                            BigDecimal los, BigDecimal benchLos) {}

    private List<GroupRow> groupRows(String org) {
        return jdbc.query("""
                select code, name, cases, avg_cost, bench_cost, avg_diff, cost_pct, los, bench_los
                from pa_group where org = ? and period = ? order by sort""",
                (rs, i) -> new GroupRow(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6),
                        rs.getInt(7), rs.getBigDecimal(8), rs.getBigDecimal(9)), org, PERIOD);
    }

    /** 引擎判定小样本：返回 {kept, merged{codes, cases, avgDiff}}。 */
    private Map<String, Object> smallSample(List<GroupRow> rows) {
        List<Map<String, Object>> groups = rows.stream()
                .map(r -> Map.<String, Object>of("code", r.code(), "cases", r.cases(), "avgDiff", r.avgDiff())).toList();
        return engine.post("/v1/portal-analysis/small-sample", Map.of("groups", groups));
    }

    @SuppressWarnings("unchecked")
    public GroupIndex groups(String org) {
        Peer peer = peer(org);
        List<GroupRow> rows = groupRows(org);
        Map<String, Object> ss = smallSample(rows);
        Set<String> kept = new HashSet<>((List<String>) ss.get("kept"));
        Map<String, Object> merged = (Map<String, Object>) ss.get("merged");
        List<GroupRef> groups = rows.stream().filter(r -> kept.contains(r.code())).map(r -> new GroupRef(r.code(), r.name())).toList();
        List<GroupRef> small = rows.stream().filter(r -> !kept.contains(r.code())).map(r -> new GroupRef(r.code(), r.name())).toList();
        List<Topic> topics = jdbc.query("select title, status, target_page, link_label from pa_related_topic where org = ? order by sort",
                (rs, i) -> new Topic(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), org);
        return new GroupIndex(PERIOD_LABEL, peer.group(), peer.orgs().size(), groups, small,
                new Other(((Number) merged.get("cases")).intValue(), ((Number) merged.get("avgDiff")).intValue()), topics);
    }

    public record Mix(String category, int own, int bench) {}

    public record Behavior(String name, double ownPct, double peerMedianPct, double deviation, boolean concern) {}

    public record Gap(String name, double own, double bench, double gap, String unit, String gapUnit, int decimals, boolean worse) {}

    public record GroupDetail(String code, String name, String periodLabel, int cases, int avgCost, int avgDiff, int costPct,
                              boolean costPctConcern, List<Mix> mix, List<Behavior> behaviors, List<Gap> gaps) {}

    private static double num(Object o) {
        return ((Number) o).doubleValue();
    }

    @SuppressWarnings("unchecked")
    public GroupDetail group(String org, String code) {
        List<GroupRow> rows = groupRows(org);
        GroupRow g = rows.stream().filter(r -> r.code().equals(code)).findFirst()
                .orElseThrow(() -> ApiException.notFound("本院无病组 " + code + " 的数据"));
        List<String> kept = (List<String>) smallSample(rows).get("kept");
        if (!kept.contains(code)) throw ApiException.notFound("病组 " + code + " 本院病例不足 30 例,已并入其他,不单独展示");

        List<Mix> mix = jdbc.query("""
                select category, own_pct, bench_pct from pa_group_mix where org = ? and period = ? and code = ? order by sort""",
                (rs, i) -> new Mix(rs.getString(1), rs.getInt(2), rs.getInt(3)), org, PERIOD, code);
        List<Map<String, Object>> beh = jdbc.query("""
                select name, own_pct, peer_median_pct, higher_is_worse from pa_group_behavior
                where org = ? and period = ? and code = ? order by sort""",
                (rs, i) -> Map.<String, Object>of("name", rs.getString(1), "ownPct", rs.getBigDecimal(2), "peerMedianPct", rs.getBigDecimal(3),
                        "higherIsWorse", rs.getBoolean(4)), org, PERIOD, code);
        int drugOwn = mix.stream().filter(m -> m.category().equals("药品")).findFirst().map(Mix::own).orElse(0);
        int drugBench = mix.stream().filter(m -> m.category().equals("药品")).findFirst().map(Mix::bench).orElse(0);
        List<Map<String, Object>> gapsIn = List.of(
                Map.of("name", "例均费用", "own", g.avgCost(), "bench", g.benchCost(), "decimals", 0),
                Map.of("name", "平均住院日", "own", g.los(), "bench", g.benchLos(), "decimals", 1),
                Map.of("name", "药品占比", "own", drugOwn, "bench", drugBench, "decimals", 0));
        Map<String, Object> r = engine.post("/v1/portal-analysis/group",
                Map.of("costPct", g.costPct(), "behaviors", beh, "gaps", gapsIn));

        List<Behavior> behaviors = ((List<Map<String, Object>>) r.get("behaviors")).stream()
                .map(b -> new Behavior((String) b.get("name"), num(b.get("ownPct")), num(b.get("peerMedianPct")), num(b.get("deviation")),
                        (Boolean) b.get("concern"))).toList();
        String[][] units = {{"元", "元"}, {"天", "天"}, {"%", "pt"}};
        List<Map<String, Object>> go = (List<Map<String, Object>>) r.get("gaps");
        List<Gap> gaps = new ArrayList<>();
        for (int i = 0; i < go.size(); i++) {
            Map<String, Object> x = go.get(i);
            gaps.add(new Gap((String) x.get("name"), num(x.get("own")), num(x.get("bench")), num(x.get("gap")), units[i][0], units[i][1],
                    i == 1 ? 1 : 0, (Boolean) x.get("worse")));
        }
        return new GroupDetail(g.code(), g.name(), PERIOD_LABEL, g.cases(), g.avgCost(), g.avgDiff(), g.costPct(),
                (Boolean) r.get("costPctConcern"), mix, behaviors, gaps);
    }

    // ================================================================ B3 对标与PK

    public record IndicatorRef(String indicator, int tier, String tierName) {}

    public record BenchIndex(String periodLabel, String peerGroup, int peerCount, List<String> tierNames, List<IndicatorRef> indicators) {}

    private static String tierName(int t) {
        return DisplayPolicyController.TIERS.get(t);
    }

    public BenchIndex benchmarks(String org) {
        Peer peer = peer(org);
        List<IndicatorRef> rows = jdbc.query("""
                select t.indicator, t.tier from benchmark_tier t join pa_bench_indicator i on i.indicator = t.indicator order by t.sort""",
                (rs, i) -> new IndicatorRef(rs.getString(1), rs.getInt(2), tierName(rs.getInt(2))));
        return new BenchIndex(PERIOD_LABEL, peer.group(), peer.orgs().size(), DisplayPolicyController.TIERS, rows);
    }

    /** 匿名分位：本院值 + 同级 P25/P50/P75 + 本院分位位置。不含他院名称、编号与数值。 */
    public record Percentile(double ownValue, Double p25, Double p50, Double p75, Integer ownPct, boolean concern, boolean suppressed,
                             String message) {}

    /** 匿名编号：「三级医院A–E」与数值，按数值降序；不含名称。 */
    public record AnonRow(String label, double value, boolean own) {}

    /** 具名对比与排行：同级排行（含名称），本院名次。 */
    public record NamedRow(int rank, String name, double value, boolean own) {}

    public record Named(String ownName, int ownRank, List<NamedRow> rows) {}

    public record BenchDetail(String indicator, int tier, String tierName, String unit, int decimals, boolean signed,
                              boolean higherIsBetter, String note, String periodLabel, String peerGroup, int peerCount,
                              Percentile percentile, List<AnonRow> anonymous, Named named) {}

    @SuppressWarnings("unchecked")
    public BenchDetail benchmark(String org, String indicator) {
        Peer peer = peer(org);
        List<Map<String, Object>> meta = jdbc.queryForList("""
                select t.tier, i.unit, i.decimals, i.signed, i.higher_is_better, i.note
                from benchmark_tier t join pa_bench_indicator i on i.indicator = t.indicator where t.indicator = ?""", indicator);
        if (meta.isEmpty()) throw ApiException.notFound("指标不存在");
        Map<String, Object> m = meta.get(0);
        int tier = ((Number) m.get("tier")).intValue();
        boolean hib = (Boolean) m.get("higher_is_better");

        // 同级机构以不透明编号送引擎（引擎不接收名称）
        Map<String, String> idToOrg = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        String ownId = null;
        int seq = 0;
        for (Map<String, Object> v : jdbc.queryForList("""
                select v.org, v.value from pa_bench_value v join pa_peer_org p on p.org = v.org
                where v.period = ? and v.indicator = ? and p.peer_group = ? order by p.sort""", PERIOD, indicator, peer.group())) {
            String id = "H" + (++seq);
            idToOrg.put(id, (String) v.get("org"));
            if (org.equals(v.get("org"))) ownId = id;
            items.add(Map.of("id", id, "value", v.get("value")));
        }
        if (ownId == null) throw ApiException.notFound("本院暂无该指标的对标数据");
        Map<String, Object> r = engine.post("/v1/portal-analysis/benchmark",
                Map.of("items", items, "ownId", ownId, "higherIsBetter", hib));

        Percentile pct = null;
        List<AnonRow> anon = null;
        Named named = null;
        switch (tier) {
            case 0 -> {
                Map<String, Object> q = (Map<String, Object>) r.get("quantiles");
                boolean sup = (Boolean) r.get("suppressed");
                pct = new Percentile(num(r.get("ownValue")), sup ? null : num(q.get("p25")), sup ? null : num(q.get("p50")),
                        sup ? null : num(q.get("p75")), sup ? null : ((Number) r.get("ownPct")).intValue(), (Boolean) r.get("concern"), sup,
                        (String) r.get("message"));
            }
            case 1 -> anon = ((List<Map<String, Object>>) r.get("rows")).stream()
                    .map(x -> new AnonRow((String) x.get("anonLabel"), num(x.get("value")), (Boolean) x.get("own"))).toList();
            case 2 -> named = new Named(org, ((Number) r.get("ownRank")).intValue(), ((List<Map<String, Object>>) r.get("ranking")).stream()
                    .map(x -> new NamedRow(((Number) x.get("rank")).intValue(), idToOrg.get((String) x.get("id")), num(x.get("value")),
                            (Boolean) x.get("own"))).toList());
            default -> throw new IllegalStateException("未知档位 " + tier);
        }
        return new BenchDetail(indicator, tier, tierName(tier), (String) m.get("unit"), ((Number) m.get("decimals")).intValue(),
                (Boolean) m.get("signed"), hib, (String) m.get("note"), PERIOD_LABEL, peer.group(), items.size(), pct, anon, named);
    }

    // ================================================================ B7 区域外数据

    public record Capacity(int visits, String note) {}

    public record Disease(String name, int visits, double fundSharePct) {}

    public record Region(String name, String scope, double fundSharePct) {}

    public record Level(String level, int sharePct) {}

    public record Offsite(String source, LocalDate asOf, String scopeNote, int visits, double fundYi, double fundSharePct,
                          Capacity capacity, List<Disease> diseases, List<Region> regions, List<Level> levels) {}

    public Offsite offsite(String org) {
        List<Map<String, Object>> s = jdbc.queryForList("""
                select as_of, source, scope_note, visits, fund_yi, fund_share_pct from pa_offsite_summary order by as_of desc limit 1""");
        if (s.isEmpty()) throw ApiException.notFound("省平台回流数据尚未到达");
        Map<String, Object> m = s.get(0);
        LocalDate asOf = ((java.sql.Date) m.get("as_of")).toLocalDate();
        List<Capacity> cap = jdbc.query("select visits, note from pa_offsite_capacity where org = ? and as_of = ?",
                (rs, i) -> new Capacity(rs.getInt(1), rs.getString(2)), org, asOf);
        return new Offsite((String) m.get("source"), asOf, (String) m.get("scope_note"), ((Number) m.get("visits")).intValue(),
                num(m.get("fund_yi")), num(m.get("fund_share_pct")), cap.isEmpty() ? null : cap.get(0),
                jdbc.query("select name, visits, fund_share_pct from pa_offsite_disease where as_of = ? order by sort",
                        (rs, i) -> new Disease(rs.getString(1), rs.getInt(2), rs.getDouble(3)), asOf),
                jdbc.query("select name, scope, fund_share_pct from pa_offsite_region where as_of = ? order by sort",
                        (rs, i) -> new Region(rs.getString(1), rs.getString(2), rs.getDouble(3)), asOf),
                jdbc.query("select level, share_pct from pa_offsite_level where as_of = ? order by sort",
                        (rs, i) -> new Level(rs.getString(1), rs.getInt(2)), asOf));
    }
}
