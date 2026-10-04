package gov.ybj.chsdpub.indicator;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.common.Texts;
import gov.ybj.chsdpub.config.AppProperties;
import gov.ybj.chsdpub.engine.EngineClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 指标可视化配置（A4）：指标目录（筛选 / 排序 / 分页）、指标卡、发布包登记、四步新建向导（草稿自动保存 → 公式校验 →
 * 以机构身份预览 → 提交上线审批）。
 * <ul>
 *   <li>「本期暂缓」= 已上线指标的来源数据源本期未按时到达（读 A3 data_source）。</li>
 *   <li>对标档位以 A13 benchmark_tier 为准；有待审批的档位切换单时标出「审批中」。</li>
 *   <li>仅内部指标不可加入发布包（服务端拒绝）；机构身份预览只下发引擎裁剪后的本院值与同级分位。</li>
 * </ul>
 */
@Service
public class IndicatorService {

    public static final List<String> TIER_NAMES = List.of("匿名分位", "匿名编号", "具名对比与排行");
    public static final List<String> GROUPS = List.of("钱", "效", "错");
    public static final List<String> DIMS = List.of("机构", "等级", "病组", "县区", "时间", "险种");
    public static final List<String> FILTER_FIELDS = List.of("手术标志", "离院方式", "险种", "入院途径", "年龄");
    public static final List<String> FILTER_OPS = List.of("=", "≠", ">", "<");
    public static final List<String> CALCS = List.of("比值", "求和", "均值", "分位", "环比/同比");
    public static final List<String> FUNCTIONS = List.of("SUM", "COUNT", "AVG", "PCTL", "RATIO", "WHERE");
    public static final List<String> AUDIENCES = List.of("定点医疗机构", "县区医保", "专家组", "省级汇总");
    public static final List<String> GRANULARITY = List.of("统筹区", "县区", "机构", "病组", "诊疗行为");
    public static final List<String> WARN_PCTS = List.of("P70", "P75", "P80", "P90");
    public static final List<String> PREVIEW_ORGS = List.of("示例市第一人民医院", "甲县人民医院", "某肛肠专科医院");
    public static final List<String> FALLBACK_TEMPLATES = List.of("分位条", "趋势线", "结构堆叠条", "分组柱", "排行条", "气泡全景", "散点",
            "归因瀑布", "流向图", "对比表");
    /** 本期发布包（A8 月告知）。 */
    public static final String PACKAGE = "2026年8月 月告知";
    public static final String CALIBER = "结算清单 v2026.1";
    private static final List<String> STATUS_ORDER = List.of("已上线", "本期暂缓", "审批中", "草稿");
    private static final Map<String, String> TAG_LABEL = Map.of("必选", "国家底稿必选", "增选", "地方增选", "仅内部", "仅内部");

    private final JdbcTemplate jdbc;
    private final EngineClient engine;
    private final ObjectMapper om;
    private final ZoneId zone;

    public IndicatorService(JdbcTemplate jdbc, EngineClient engine, ObjectMapper om, AppProperties props) {
        this.jdbc = jdbc;
        this.engine = engine;
        this.om = om;
        this.zone = ZoneId.of(props.zone());
    }

    // ================================================================ 列表

    public record Row(long id, String code, String name, String tag, String tagLabel, String grp, String domain, String source, String freq,
                      Integer tier, String tierLabel, String tierPending, String scope, String version, String status, String statusTip,
                      boolean internal, boolean inPackage) {}

    public record Page(List<Row> rows, int total, int page, int size, int pages, List<String> domains, List<String> tags, String packageName) {}

    private List<Row> all() {
        return jdbc.query("""
                select i.id, i.code, i.name, i.tag, i.grp, i.domain, i.source, i.freq, coalesce(b.tier, i.tier), r.to_tier,
                       i.scope, i.version, i.status, d.name as late,
                       exists (select 1 from ind_package_item p where p.indicator_id = i.id and p.package = ?)
                from ind_indicator i
                left join benchmark_tier b on b.indicator = i.name and i.tag <> '仅内部'
                left join tier_change_request r on r.indicator = i.name and r.status = 'PENDING'
                left join data_source d on d.name = i.source and d.status = 'LATE'
                order by i.sort, i.id""", (rs, n) -> {
            String tag = rs.getString(4);
            boolean internal = "仅内部".equals(tag);
            Integer tier = internal ? null : (Integer) rs.getObject(9);
            Integer pending = (Integer) rs.getObject(10);
            String status = rs.getString(13);
            String late = rs.getString(14);
            String tip = null;
            if ("已上线".equals(status) && late != null) {
                status = "本期暂缓";
                tip = "依赖数据源「" + late + "」本期未按时到达";
            } else if ("审批中".equals(status)) {
                tip = "已提交召集人审批,批准后上线";
            }
            return new Row(rs.getLong(1), rs.getString(2), rs.getString(3), tag, TAG_LABEL.get(tag), rs.getString(5), rs.getString(6),
                    rs.getString(7), rs.getString(8), tier, tier == null ? "—" : TIER_NAMES.get(tier),
                    pending == null || internal ? null : TIER_NAMES.get(pending), rs.getString(11), rs.getString(12), status, tip, internal,
                    rs.getBoolean(15));
        }, PACKAGE);
    }

    public Page list(String grp, String domain, String tag, String q, String sort, String dir, int page, int size) {
        if (size < 1 || size > 100) throw ApiException.validation("每页条数须在 1–100 之间");
        List<Row> rows = all();
        List<String> domains = rows.stream().map(Row::domain).distinct().toList();
        String kw = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Row> f = rows.stream()
                .filter(r -> Texts.blank(grp) || "全部".equals(grp) || r.grp().equals(grp))
                .filter(r -> Texts.blank(domain) || r.domain().equals(domain))
                .filter(r -> Texts.blank(tag) || r.tag().equals(tag))
                .filter(r -> kw.isEmpty() || r.name().toLowerCase(Locale.ROOT).contains(kw) || r.code().toLowerCase(Locale.ROOT).contains(kw))
                .collect(Collectors.toCollection(ArrayList::new));
        Comparator<Row> cmp = null;
        if ("tier".equals(sort)) cmp = Comparator.comparingInt(r -> r.tier() == null ? 9 : r.tier());
        else if ("status".equals(sort)) cmp = Comparator.comparingInt(r -> STATUS_ORDER.indexOf(r.status()));
        else if (!Texts.blank(sort)) throw ApiException.validation("排序字段须为 tier 或 status");
        if (cmp != null) f.sort("desc".equals(dir) ? cmp.reversed() : cmp);
        int total = f.size();
        int pages = Math.max(1, (total + size - 1) / size);
        int p = Math.min(Math.max(1, page), pages);
        List<Row> slice = f.subList(Math.min(total, (p - 1) * size), Math.min(total, p * size));
        return new Page(List.copyOf(slice), total, p, size, pages, domains, List.of("必选", "增选", "仅内部"), PACKAGE);
    }

    private Row row(long id) {
        return all().stream().filter(r -> r.id() == id).findFirst().orElseThrow(() -> ApiException.notFound("指标不存在"));
    }

    // ================================================================ 指标卡

    public record Change(String label, LocalDate date, String author, String note) {}

    public record Card(Row row, String owner, String formula, String ruleVersion, List<String> lineage, List<Change> changes) {}

    public Card card(long id) {
        Row r = row(id);
        Map<String, Object> m = jdbc.queryForMap("""
                select i.owner, i.formula, i.rule_version, coalesce(l.batch_no, i.batch_no) batch, coalesce(l.source_tables, i.source_tables) tbl,
                       coalesce(l.card_version, i.version) card
                from ind_indicator i left join indicator_lineage l on l.indicator = i.name where i.id = ?""", id);
        List<String> lineage = new ArrayList<>();
        lineage.add("指标卡 " + m.get("card"));
        lineage.add("取数批次 " + m.get("batch"));
        for (String t : ((String) m.get("tbl")).split("\\s*·\\s*")) lineage.add(t.trim());
        List<Change> changes = jdbc.query("select label, changed_on, author, note from ind_change_log where indicator_id = ? order by sort",
                (rs, i) -> new Change(rs.getString(1), rs.getObject(2, LocalDate.class), rs.getString(3), rs.getString(4)), id);
        return new Card(r, (String) m.get("owner"), (String) m.get("formula"), (String) m.get("rule_version"), lineage, changes);
    }

    // ================================================================ 发布包

    @Transactional
    public Map<String, Object> addToPackage(long id, AuthUser u) {
        Row r = row(id);
        if (r.internal()) throw ApiException.conflict("仅内部指标不可加入发布包");
        if (r.inPackage()) throw ApiException.conflict("「" + r.name() + "」已在 " + PACKAGE + "发布包中");
        jdbc.update("insert into ind_package_item (indicator_id, package, added_by) values (?,?,?)", id, PACKAGE, u.userId());
        return Map.of("row", row(id), "message", "「" + r.name() + "」已加入 " + PACKAGE + "发布包");
    }

    @Transactional
    public Map<String, Object> removeFromPackage(long id) {
        Row r = row(id);
        if (jdbc.update("delete from ind_package_item where indicator_id = ? and package = ?", id, PACKAGE) == 0)
            throw ApiException.conflict("「" + r.name() + "」不在发布包中");
        return Map.of("row", row(id), "message", "「" + r.name() + "」已移出 " + PACKAGE + "发布包");
    }

    // ================================================================ 向导元数据

    public record Atom(String name, String unit, String source) {}

    public record PeerGroup(String name, int n) {}

    public Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("atoms", jdbc.query("select name, unit, source from ind_atom order by sort", (rs, i) -> new Atom(rs.getString(1), rs.getString(2), rs.getString(3))));
        m.put("dims", DIMS);
        m.put("filterFields", FILTER_FIELDS);
        m.put("filterOps", FILTER_OPS);
        m.put("calcs", CALCS);
        m.put("functions", FUNCTIONS);
        m.put("peerGroups", peerGroups());
        List<String> tpl = jdbc.queryForList("select name from chart_template order by sort", String.class);
        m.put("templates", tpl.isEmpty() ? FALLBACK_TEMPLATES : tpl);
        m.put("audiences", AUDIENCES);
        m.put("granularity", GRANULARITY);
        m.put("warnPcts", WARN_PCTS);
        m.put("previewOrgs", PREVIEW_ORGS);
        m.put("tierNames", TIER_NAMES);
        m.put("groups", GROUPS);
        m.put("domains", jdbc.queryForList("select domain from ind_indicator group by domain order by min(sort)", String.class));
        m.put("caliber", CALIBER);
        return m;
    }

    private List<PeerGroup> peerGroups() {
        return jdbc.query("""
                select g.name, count(o.name) from ind_peer_group g left join ind_org o on o.peer_group = g.name group by g.name, g.sort order by g.sort""",
                (rs, i) -> new PeerGroup(rs.getString(1), rs.getInt(2)));
    }

    // ================================================================ 草稿

    public record Filter(String field, String op, String value) {}

    /** 向导配置（草稿 JSON）。 */
    public record Config(String name, String grp, String domain, String numerator, String denominator, List<String> dims,
                         List<Filter> filters, String calc, String formula, Integer minOrgs, Integer minCases, String warnPct,
                         Integer warnRise, Boolean internal, Integer tier, String template, String title, String unit, String note,
                         List<String> audiences, String granularity) {}

    /** 最近一次被召集人驳回的上线审批(草稿已解锁,可修改后重新提交)。 */
    public record Rejection(String approvalNo, String opinion, String decidedBy, OffsetDateTime decidedAt) {}

    public record Draft(long id, String version, OffsetDateTime savedAt, Config config, boolean submitted, String approvalNo,
                        Long indicatorId, Rejection rejection) {}

    static Config defaults() {
        return new Config("术前平均住院日", "效", "DRG月度运行", "术前住院天数", "手术出院人次", List.of("机构", "等级", "病组", "时间"),
                List.of(new Filter("手术标志", "=", "1"), new Filter("离院方式", "≠", "死亡")), "比值",
                """
                        术前平均住院日 =
                          SUM(术前住院天数)
                          / COUNT(手术出院人次)
                        WHERE 手术标志 = 1 AND 离院方式 != '死亡'
                        GROUP BY 机构, 等级, 病组, 时间""",
                5, 30, "P75", 10, false, 0, "分位条", "术前平均住院日 · 同级分位", "天",
                "术前等待时间越短,床位周转与费用控制通常越好。本指标为同级比较,不作为考核指标。",
                List.of("定点医疗机构", "县区医保", "专家组"), "病组");
    }

    private Config parse(String json) {
        try {
            return om.readValue(json, Config.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String json(Config c) {
        try {
            return om.writeValueAsString(c);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Draft draft(long id, AuthUser u) {
        List<Draft> r = jdbc.query("""
                select id, version, saved_at, config::text, submitted_at is not null, approval_no, indicator_id
                from ind_draft where id = ? and created_by = ?""",
                (rs, i) -> new Draft(rs.getLong(1), rs.getString(2), rs.getObject(3, OffsetDateTime.class), parse(rs.getString(4)),
                        rs.getBoolean(5), rs.getString(6), (Long) rs.getObject(7), null), id, u.userId());
        if (r.isEmpty()) throw ApiException.notFound("草稿不存在");
        Draft d = r.get(0);
        if (d.submitted()) return d;
        List<Rejection> rej = jdbc.query("""
                select x.approval_no, x.opinion, u.name, x.decided_at from pub_indicator_decision x join app_user u on u.id = x.decided_by
                where x.draft_id = ? and x.decision = 'REJECTED' order by x.id desc limit 1""",
                (rs, i) -> new Rejection(rs.getString(1), rs.getString(2), rs.getString(3), rs.getObject(4, OffsetDateTime.class)), id);
        return rej.isEmpty() ? d : new Draft(d.id(), d.version(), d.savedAt(), d.config(), false, d.approvalNo(), d.indicatorId(), rej.get(0));
    }

    /** 新建指标：有未提交草稿则续编（草稿自动保存），否则按默认配置新建。 */
    @Transactional
    public Draft openDraft(AuthUser u) {
        List<Long> open = jdbc.queryForList("select id from ind_draft where created_by = ? and submitted_at is null order by id desc limit 1",
                Long.class, u.userId());
        if (!open.isEmpty()) return draft(open.get(0), u);
        Long id = jdbc.queryForObject("insert into ind_draft (created_by, config) values (?, ?::jsonb) returning id", Long.class, u.userId(),
                json(defaults()));
        return draft(id, u);
    }

    public Draft getDraft(long id, AuthUser u) {
        return draft(id, u);
    }

    /** 放弃当前草稿，按默认配置重新开始。 */
    @Transactional
    public Draft resetDraft(long id, AuthUser u) {
        Draft d = draft(id, u);
        if (d.submitted()) throw ApiException.conflict("已提交上线审批,草稿已锁定");
        jdbc.update("update ind_draft set config = ?::jsonb, saved_at = now() where id = ?", json(defaults()), id);
        return draft(id, u);
    }

    private Config checked(Config c) {
        if (c == null) throw ApiException.validation("缺少配置");
        if (Texts.blank(c.formula()) || c.formula().length() > 4000) throw ApiException.validation("公式不能为空且不超过 4000 字");
        if (c.minOrgs() == null || c.minOrgs() < 1 || c.minOrgs() > 50) throw ApiException.validation("同级机构数阈值须在 1–50 之间");
        if (c.minCases() == null || c.minCases() < 0 || c.minCases() > 1000) throw ApiException.validation("病例数阈值须在 0–1000 之间");
        if (c.warnRise() == null || c.warnRise() < 0 || c.warnRise() > 100) throw ApiException.validation("环比上升阈值须在 0–100% 之间");
        if (c.tier() == null || c.tier() < 0 || c.tier() > 2) throw ApiException.validation("对标档位无效");
        if (!WARN_PCTS.contains(c.warnPct())) throw ApiException.validation("预警分位须为 " + String.join(" / ", WARN_PCTS));
        if (!CALCS.contains(c.calc())) throw ApiException.validation("计算方式无效");
        if (!GROUPS.contains(c.grp())) throw ApiException.validation("分组须为 钱 / 效 / 错");
        if (c.dims() == null || !DIMS.containsAll(c.dims())) throw ApiException.validation("维度无效");
        if (!GRANULARITY.contains(c.granularity())) throw ApiException.validation("粒度上限无效");
        if ("诊疗行为".equals(c.granularity())) throw ApiException.validation("发布区最细到病组;诊疗行为级仅分析监测区可见");
        if (c.audiences() == null || !AUDIENCES.containsAll(c.audiences())) throw ApiException.validation("受众无效");
        if (c.filters() != null) for (Filter f : c.filters()) {
            if (!FILTER_FIELDS.contains(f.field()) || !FILTER_OPS.contains(f.op())) throw ApiException.validation("过滤条件无效");
        }
        for (String s : new String[]{c.title(), c.unit(), c.note(), c.name()}) {
            if (s != null && s.length() > 256) throw ApiException.validation("文字内容过长");
        }
        // 仅内部：受众固定为医保局内部，对标档位不适用
        boolean internal = Boolean.TRUE.equals(c.internal());
        return new Config(Texts.truncate(c.name(), 32), c.grp(), c.domain(), c.numerator(), c.denominator(), c.dims(),
                c.filters() == null ? List.of() : c.filters(), c.calc(), c.formula(), c.minOrgs(), c.minCases(), c.warnPct(), c.warnRise(),
                internal, internal ? 0 : c.tier(), c.template(), c.title(), c.unit(), c.note(), internal ? List.of() : c.audiences(),
                c.granularity());
    }

    @Transactional
    public Draft saveDraft(long id, Config c, AuthUser u) {
        Draft d = draft(id, u);
        if (d.submitted()) throw ApiException.conflict("已提交上线审批,草稿已锁定");
        jdbc.update("update ind_draft set config = ?::jsonb, saved_at = now() where id = ?", json(checked(c)), id);
        return draft(id, u);
    }

    // ================================================================ 公式校验（引擎）

    private List<Map<String, Object>> atomsForEngine() {
        return jdbc.query("select name, cases from ind_atom order by sort", (rs, i) -> Map.<String, Object>of("name", rs.getString(1), "cases", rs.getInt(2)));
    }

    public Map<String, Object> validate(String formula, List<String> dims) {
        if (formula == null || formula.length() > 4000) throw ApiException.validation("公式不能超过 4000 字");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("formula", formula);
        body.put("atoms", atomsForEngine());
        body.put("dims", dims == null ? List.of() : dims);
        body.put("allDims", DIMS);
        body.put("filterFields", FILTER_FIELDS);
        body.put("caliber", CALIBER);
        return engine.post("/v1/indicator/formula/validate", body);
    }

    // ================================================================ 以机构身份预览（引擎裁剪）

    public Map<String, Object> preview(long id, String org, AuthUser u) {
        Config c = draft(id, u).config();
        if (org != null && !org.isBlank()) {
            Integer n = jdbc.queryForObject("select count(*) from ind_org where name = ?", Integer.class, org);
            if (n == null || n == 0) throw ApiException.notFound("机构不存在");
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        Map<String, List<Map<String, Object>>> byGroup = new LinkedHashMap<>();
        for (PeerGroup g : peerGroups()) byGroup.put(g.name(), new ArrayList<>());
        jdbc.query("""
                select o.peer_group, v.org, v.value, v.cases from ind_preview_value v join ind_org o on o.name = v.org
                where v.numerator = ? and v.denominator = ? order by o.sort""", rs -> {
            byGroup.get(rs.getString(1)).add(Map.of("org", rs.getString(2), "value", rs.getBigDecimal(3), "cases", rs.getInt(4)));
        }, c.numerator(), c.denominator());
        if (byGroup.values().stream().allMatch(List::isEmpty))
            throw ApiException.conflict("所选原子指标组合(" + c.numerator() + " ÷ " + c.denominator() + ")本期无可预览数据");
        byGroup.forEach((k, v) -> groups.add(Map.of("name", k, "orgs", v)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("viewerOrg", org == null || org.isBlank() ? null : org);
        body.put("groups", groups);
        body.put("minOrgs", c.minOrgs());
        body.put("minCases", c.minCases());
        body.put("internal", c.internal());
        Map<String, Object> r = new LinkedHashMap<>(engine.post("/v1/indicator/preview", body));
        // 审批通过前一律按「匿名分位」发布
        r.put("tierLabel", TIER_NAMES.get(0));
        r.put("unit", c.unit());
        r.put("indicator", c.name());
        return r;
    }

    // ================================================================ 算法沙盘(无状态试算:不落草稿,只读本期预览数据)

    public List<Map<String, Object>> sandboxCombos() {
        return jdbc.queryForList("select distinct numerator, denominator from ind_preview_value order by numerator, denominator");
    }

    public record SandboxReq(String numerator, String denominator, Integer minOrgs, Integer minCases) {}

    /** 按给定阈值调用引擎试算,并附各机构明细(分析监测区内部使用,机构不可达)。 */
    public Map<String, Object> sandbox(SandboxReq req) {
        int minOrgs = req.minOrgs() == null ? 5 : Math.max(1, Math.min(50, req.minOrgs()));
        int minCases = req.minCases() == null ? 30 : Math.max(0, Math.min(100000, req.minCases()));
        Map<String, List<Map<String, Object>>> byGroup = new LinkedHashMap<>();
        for (PeerGroup g : peerGroups()) byGroup.put(g.name(), new ArrayList<>());
        jdbc.query("""
                select o.peer_group, v.org, v.value, v.cases from ind_preview_value v join ind_org o on o.name = v.org
                where v.numerator = ? and v.denominator = ? order by o.sort""", rs -> {
            byGroup.get(rs.getString(1)).add(Map.of("org", rs.getString(2), "value", rs.getBigDecimal(3), "cases", rs.getInt(4)));
        }, req.numerator(), req.denominator());
        if (byGroup.values().stream().allMatch(List::isEmpty)) throw ApiException.conflict("该原子指标组合本期无可试算数据");
        List<Map<String, Object>> groups = new ArrayList<>();
        byGroup.forEach((k, v) -> groups.add(Map.of("name", k, "orgs", v)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("viewerOrg", null);
        body.put("groups", groups);
        body.put("minOrgs", minOrgs);
        body.put("minCases", minCases);
        body.put("internal", false);
        Map<String, Object> r = new LinkedHashMap<>(engine.post("/v1/indicator/preview", body));
        r.put("minOrgs", minOrgs);
        r.put("minCases", minCases);
        r.put("detail", groups);
        return r;
    }

    // ================================================================ 提交上线审批

    @Transactional
    public Map<String, Object> submit(long id, AuthUser u) {
        Draft d = draft(id, u);
        if (d.submitted()) throw ApiException.conflict("已提交上线审批(审批单 " + d.approvalNo() + ")");
        Config c = checked(d.config());
        Map<String, Object> v = validate(c.formula(), c.dims());
        if (!Boolean.TRUE.equals(v.get("ok"))) throw ApiException.validation("公式校验未通过:" + v.get("message"));
        String name = (String) v.get("name");
        if (Texts.blank(name) || name.length() > 32) throw ApiException.validation("指标名称须为 1–32 字");
        Integer dup = jdbc.queryForObject("select count(*) from ind_indicator where name = ?", Integer.class, name);
        if (dup != null && dup > 0) throw ApiException.conflict("指标「" + name + "」已存在");
        if ("排行条".equals(c.template()) && c.tier() != 2) throw ApiException.validation("排行条仅用于「具名对比与排行」档位");
        boolean internal = Boolean.TRUE.equals(c.internal());

        List<Map<String, Object>> src = jdbc.queryForList("select source from ind_atom where name = ?", c.denominator() == null ? c.numerator() : c.denominator());
        String source = src.isEmpty() ? "结算明细与DRG入组" : (String) src.get(0).get("source");
        Map<String, Object> lineage = jdbc.queryForMap("select batch_no, source_tables from ind_indicator where source = ? order by sort limit 1", source);
        String prefix = switch (c.grp()) { case "钱" -> "Q"; case "效" -> "X"; default -> "C"; };
        Integer seq = jdbc.queryForObject("select count(*) + 1 from ind_indicator where grp = ?", Integer.class, c.grp());
        String code = String.format("IND-%s%02d", prefix, seq);
        String scope = internal ? "医保局内部"
                : c.audiences().contains("定点医疗机构") ? "全市定点机构"
                : c.audiences().isEmpty() ? "医保局内部" : String.join(" · ", c.audiences());
        String formulaLine = c.formula().replaceAll("\\s*\\n\\s*", " ").trim();
        String rule = CALIBER.replace(" ", "口径 ") + (c.filters().isEmpty() ? "" : " · " + c.filters().stream()
                .map(f -> f.field() + " " + f.op() + " " + f.value()).collect(Collectors.joining(" 且 ")));
        Integer sort = jdbc.queryForObject("select coalesce(max(sort), 0) + 1 from ind_indicator", Integer.class);
        Long indId = jdbc.queryForObject("""
                insert into ind_indicator (code, name, grp, domain, source, freq, tier, scope, version, status, tag, owner, formula, rule_version,
                                           batch_no, source_tables, sort)
                values (?,?,?,?,?,'月',?,?,?,'审批中',?,?,?,?,?,?,?) returning id""", Long.class,
                code, name, c.grp(), Texts.blank(c.domain()) ? "DRG月度运行" : c.domain(), source, internal ? null : 0, scope, d.version(),
                internal ? "仅内部" : "增选", u.name() + "(" + u.roleLabel() + ")· 复核:专家组 刘教授", Texts.truncate(formulaLine, 512),
                Texts.truncate(rule, 128), lineage.get("batch_no"), lineage.get("source_tables"), sort);
        int year = LocalDate.now(zone).getYear();
        Long n = jdbc.queryForObject("select nextval('ind_approval_seq')", Long.class);
        String no = String.format("ZB-%d-%04d", year, n);
        jdbc.update("insert into ind_change_log (indicator_id, label, changed_on, author, note, sort) values (?,?,?,?,?,1)", indId, d.version(),
                LocalDate.now(zone), u.name(), "新建指标,提交上线审批 " + no);
        jdbc.update("update ind_draft set submitted_at = now(), approval_no = ?, indicator_id = ?, config = ?::jsonb where id = ?", no, indId,
                json(c), id);
        String tierNote = !internal && c.tier() != 0
                ? "对标档位「" + TIER_NAMES.get(c.tier()) + "」随本审批单报召集人审批,审批通过前按「匿名分位」发布。" : null;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("approvalNo", no);
        r.put("indicatorId", indId);
        r.put("code", code);
        r.put("message", "已提交上线审批 · 审批单 " + no);
        r.put("detail", internal ? "召集人批准后,该指标仅在分析监测区可见,不进入任何发布包。"
                : "召集人批准后,该指标正式上线(归入“" + c.grp() + "”),并可纳入发布包。");
        if (tierNote != null) r.put("tierNote", tierNote);
        r.put("draft", draft(id, u));
        return r;
    }
}
