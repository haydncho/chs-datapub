package gov.ybj.chsdpub.collection;

import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.engine.EngineClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 数据归集中心（A3）：六步流水线、十类数据源到数与质量、依赖指标、质量报告、血缘查询。
 * 数据源未按时到达 → 依赖指标「本期暂缓」；全部到数后才能完成质量校验并进入报告生成（流程 1）。
 */
@Service
public class CollectionService {

    private static final String[] STEPS = {"接入", "标准化", "主数据对齐", "质量校验", "主题库", "指标集市"};

    private final JdbcTemplate jdbc;
    private final EngineClient engine;

    public CollectionService(JdbcTemplate jdbc, EngineClient engine) {
        this.jdbc = jdbc;
        this.engine = engine;
    }

    record Period(String period, boolean qcDone, int rules, int orgs, int doctors, int topics, int indicators,
                  BigDecimal completeness, BigDecimal consistency) {}

    public record Source(long id, String name, String provider, String mode, String frequency, String status,
                         String statusLabel, Integer score, String dueDate) {}

    public record Step(String no, String name, String state, String count) {}

    public record OrgQc(String org, BigDecimal comorbidityPct, BigDecimal listQcPct, Boolean comorbidityLow, Boolean listQcLow) {}

    public record Quality(BigDecimal completenessPct, BigDecimal consistencyPct, Double timelinessPct, List<OrgQc> orgs,
                          boolean engineAvailable) {}

    public record Overview(String period, String title, boolean allArrived, boolean qcDone, List<Step> pipeline,
                           List<Source> sources, Quality quality, List<String> lineageIndicators) {}

    public record DepIndicator(String name, boolean suspended) {}

    public record SourceDetail(long id, String name, boolean late, String dueDate, List<DepIndicator> indicators) {}

    public record LineageStep(String key, String value, boolean missing) {}

    private Period period(String p) {
        List<Period> r = jdbc.query("""
                select period, qc_done, rule_count, org_count, doctor_count, topic_count, indicator_total, completeness_pct, consistency_pct
                from collection_period where period = ?""",
                (rs, i) -> new Period(rs.getString(1), rs.getBoolean(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6),
                        rs.getInt(7), rs.getBigDecimal(8), rs.getBigDecimal(9)), p);
        if (r.isEmpty()) throw ApiException.notFound("期次 " + p + " 不存在");
        return r.get(0);
    }

    private List<Source> sources() {
        return jdbc.query("""
                select id, name, provider, access_mode, frequency, status, part_received, part_total, quality_score, due_date
                from data_source order by sort""", (rs, i) -> {
            String st = rs.getString(6);
            String label = switch (st) {
                case "LATE" -> "未按时到达";
                case "PART" -> "部分到数 " + rs.getInt(7) + "/" + rs.getInt(8);
                default -> "已到数";
            };
            return new Source(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), st, label,
                    "LATE".equals(st) ? null : rs.getInt(9), rs.getString(10));
        });
    }

    private int suspendedCount() {
        Integer n = jdbc.queryForObject("""
                select count(*) from data_source_indicator i join data_source s on s.id = i.source_id where s.status = 'LATE'""",
                Integer.class);
        return n == null ? 0 : n;
    }

    public Overview overview(String p) {
        Period per = period(p);
        List<Source> src = sources();
        long arrived = src.stream().filter(s -> !"LATE".equals(s.status())).count();
        boolean all = arrived == src.size();
        int suspended = suspendedCount();
        String[] counts = {
                arrived + "/" + src.size() + " 数据源",
                arrived + "/" + arrived + " 已标准化",
                "机构 " + per.orgs() + " · 医师 " + String.format("%,d", per.doctors()),
                "规则 " + per.rules() + " 条 · " + (per.qcDone() ? "通过" : "校验中"),
                "主题域 " + per.topics() + " 个",
                (per.indicators() - suspended) + "/" + per.indicators() + " 指标可计算" + (suspended > 0 ? " · " + suspended + " 项暂缓" : ""),
        };
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < STEPS.length; i++) {
            String state = i < 3 ? "done" : i == 3 ? (per.qcDone() ? "done" : "run") : (per.qcDone() ? "done" : "wait");
            steps.add(new Step(String.format("%02d", i + 1), STEPS[i], state, counts[i]));
        }
        List<String> lin = jdbc.queryForList("select indicator from indicator_lineage order by sort", String.class);
        String title = p.substring(0, 4) + "年" + Integer.parseInt(p.substring(5)) + "月 归集流水线";
        return new Overview(p, title, all, per.qcDone(), steps, src, quality(per, src), lin);
    }

    private List<Map<String, Object>> orgRows() {
        return jdbc.queryForList("select org, comorbidity_pct, list_qc_pct from org_quality order by sort");
    }

    private Map<String, Object> engineCheck(List<Source> src, List<Map<String, Object>> orgs) {
        List<Map<String, Object>> s = src.stream().map(x -> Map.<String, Object>of("name", x.name(), "status", x.status(),
                "qualityScore", x.score() == null ? 0 : x.score())).toList();
        List<Map<String, Object>> o = orgs.stream().map(x -> Map.<String, Object>of("org", x.get("org"),
                "comorbidityPct", x.get("comorbidity_pct"), "listQcPct", x.get("list_qc_pct"))).toList();
        return engine.post("/v1/quality/check", Map.of("sources", s, "orgs", o));
    }

    @SuppressWarnings("unchecked")
    private Quality quality(Period per, List<Source> src) {
        List<Map<String, Object>> orgs = orgRows();
        Map<String, Map<String, Object>> flags = new HashMap<>();
        Double timeliness = null;
        boolean ok = true;
        try {
            Map<String, Object> r = engineCheck(src, orgs);
            timeliness = ((Number) r.get("timelinessPct")).doubleValue();
            for (Object f : (List<Object>) r.get("flags")) {
                Map<String, Object> m = (Map<String, Object>) f;
                flags.put((String) m.get("org"), m);
            }
        } catch (ApiException e) {
            ok = false; // 引擎不可用：质量报告的计算项显示「—」，不在服务端自行计算
        }
        List<OrgQc> rows = orgs.stream().map(x -> {
            Map<String, Object> f = flags.get((String) x.get("org"));
            return new OrgQc((String) x.get("org"), (BigDecimal) x.get("comorbidity_pct"), (BigDecimal) x.get("list_qc_pct"),
                    f == null ? null : (Boolean) f.get("comorbidityLow"), f == null ? null : (Boolean) f.get("listQcLow"));
        }).toList();
        return new Quality(per.completeness(), per.consistency(), timeliness, rows, ok);
    }

    public SourceDetail source(long id) {
        Source s = sources().stream().filter(x -> x.id() == id).findFirst().orElseThrow(() -> ApiException.notFound("数据源不存在"));
        boolean late = "LATE".equals(s.status());
        List<DepIndicator> deps = jdbc.query("select indicator from data_source_indicator where source_id = ? order by sort",
                (rs, i) -> new DepIndicator(rs.getString(1), late), id);
        return new SourceDetail(id, s.name(), late, s.dueDate(), deps);
    }

    public List<LineageStep> lineage(String indicator) {
        List<Map<String, Object>> r = jdbc.queryForList("""
                select l.card_version, l.batch_no, l.theme_table, l.source_tables, s.name, s.status
                from indicator_lineage l join data_source s on s.id = l.source_id where l.indicator = ?""", indicator);
        if (r.isEmpty()) throw ApiException.notFound("指标 " + indicator + " 无血缘记录");
        Map<String, Object> m = r.get(0);
        boolean missing = "LATE".equals(m.get("status"));
        return List.of(
                new LineageStep("指标卡版本", indicator + " " + m.get("card_version"), false),
                new LineageStep("取数批次", missing ? "本期未生成(数据源未到达)" : (String) m.get("batch_no"), missing),
                new LineageStep("主题库表", (String) m.get("theme_table"), false),
                new LineageStep("源表", (String) m.get("source_tables"), false),
                new LineageStep("数据源", (String) m.get("name"), false));
    }

    /** 到数登记（省平台回流等外部数据到达后由经办登记，或由交换任务回调）。 */
    @Transactional
    public Map<String, Object> markArrived(long id) {
        SourceDetail s = source(id);
        if (!s.late()) throw ApiException.conflict("该数据源本期已到数");
        jdbc.update("update data_source set status = 'OK' where id = ?", id);
        return Map.of("message", s.name() + "数据已到达," + s.indicators().size() + " 项指标恢复计算");
    }

    @Transactional
    public Map<String, Object> qualityCheck(String p) {
        Period per = period(p);
        if (per.qcDone()) throw ApiException.conflict("本期质量校验已完成");
        Map<String, Object> r = engineCheck(sources(), orgRows());
        if (!Boolean.TRUE.equals(r.get("passed"))) throw ApiException.conflict((String) r.get("message"));
        jdbc.update("update collection_period set qc_done = true, qc_at = now() where period = ?", p);
        return Map.of("message", "质量校验通过,指标集市已更新");
    }
}
