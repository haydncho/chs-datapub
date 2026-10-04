package gov.ybj.chsdpub.publish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ybj.chsdpub.audit.AuditService;
import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.auth.CurrentUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.common.Roles;
import gov.ybj.chsdpub.common.Texts;
import gov.ybj.chsdpub.config.AppProperties;
import gov.ybj.chsdpub.engine.EngineClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 发布工作流（A8）：数据从分析监测区进入发布区的唯一通道。
 * 十步流程的步名取自 A9 流程模板节点；第 5 步「召集人审批」为必经节点，未批准不外发；驳回固定退回「分析成稿」。
 * 定向范围的覆盖机构数与名单由引擎计算。A5 生成的报告草稿作为「月告知」待办进入第 3 步。
 */
@Service
public class PublishService {

    public static final List<String> GROUPS = List.of("月告知", "季公布", "年通报", "病种与机构专题", "预警提醒函", "更正与撤回");
    public static final List<String> DIMS = List.of("tiers", "districts", "batch", "alliance", "group");
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd");

    private final JdbcTemplate jdbc;
    private final EngineClient engine;
    private final ConvenerGuard guard;
    private final ObjectMapper om;
    private final ZoneId zone;
    private final IndicatorApprovalService indicatorApprovals;
    private final AuditService audit;

    public PublishService(JdbcTemplate jdbc, EngineClient engine, ConvenerGuard guard, ObjectMapper om, AppProperties props,
                          IndicatorApprovalService indicatorApprovals, AuditService audit) {
        this.indicatorApprovals = indicatorApprovals;
        this.audit = audit;
        this.jdbc = jdbc;
        this.engine = engine;
        this.guard = guard;
        this.om = om;
        this.zone = ZoneId.of(props.zone());
    }

    // ================================================================ 数据结构

    public record Scope(List<String> tiers, List<String> districts, String batch, String alliance, String group) {}

    public record Node(int idx, String name, String sub, boolean gate, int days) {}

    record FlowRow(long id, String kind, long templateId, String name, String subject, String packageVersion, int step,
                   LocalDate dueDate, String duePrefix, Scope scope, Long draftId, String action, String explanation) {}

    public record TodoItem(String type, long id, String name, String status, String statusTone, String due, String dueTone, String source) {}

    public record TodoGroup(String group, List<TodoItem> items) {}

    public record FlowInfo(long id, String kind, String name, String subject, String packageVersion, int step, String stepLabel,
                           boolean archived, int gateIdx, int rejectIdx, String action, Long reportDraftId, boolean scopeLocked,
                           String nextStepName) {}

    public record PackageItem(String name, String detail) {}

    public record Package(List<PackageItem> items, List<String> excluded) {}

    public record Option(String value, String label) {}

    public record Log(String at, String who, String what) {}

    public record Release(int version, String title, String publishedOn, int signed, int total, String status, String note) {}

    public record Corrections(String subject, List<Release> releases, String action, String explanation, boolean canInitiate) {}

    /** 批准发布后第 6 步起的推进:定向发布 → 签收查阅 → 意见申诉 → 答复整改 → 归档。 */
    public record Advance(int toStep, String toName, String label, String hint, boolean allowed, String blocked, Integer signed,
                          Integer total, int opinions, int openOpinions) {}

    public record FlowDetail(FlowInfo flow, List<Node> steps, Package pkg, Scope scope, Map<String, List<Option>> options,
                             Map<String, Object> coverage, List<Log> logs, Corrections corrections, int openCheckOpinions,
                             boolean canApprove, Advance advance) {}

    public record AudienceLine(String label, String value, String tone) {}

    public record AudienceVersion(int id, String title, String subtitle, String note, String noteTone, List<AudienceLine> lines) {}

    // ================================================================ 查询

    private Scope parseScope(String json) {
        try {
            return om.readValue(json, Scope.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("定向范围格式错误", e);
        }
    }

    private String json(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private FlowRow mapFlow(ResultSet rs) throws SQLException {
        return new FlowRow(rs.getLong("id"), rs.getString("kind"), rs.getLong("template_id"), rs.getString("name"), rs.getString("subject"),
                rs.getString("package_version"), rs.getInt("step"), rs.getObject("due_date", LocalDate.class), rs.getString("due_prefix"),
                parseScope(rs.getString("scope")), (Long) rs.getObject("report_draft_id"), rs.getString("action"), rs.getString("explanation"));
    }

    private static final String FLOW_COLS = """
            id, kind, template_id, name, subject, package_version, step, due_date, due_prefix, scope::text as scope,
            report_draft_id, action, explanation""";

    private FlowRow flow(long id, boolean lock) {
        List<FlowRow> r = jdbc.query("select " + FLOW_COLS + " from pub_flow where id = ?" + (lock ? " for update" : ""),
                (rs, i) -> mapFlow(rs), id);
        if (r.isEmpty()) throw ApiException.notFound("发布流程不存在");
        return r.get(0);
    }

    /** 流程模板节点（A9）：步名、副标（并行的机构同步核对）、必经节点、时限。 */
    private List<Node> nodes(long templateId) {
        List<Node> n = jdbc.query("select idx, name, handler, gate, days from flow_node where template_id = ? order by idx",
                (rs, i) -> new Node(rs.getInt(1), rs.getString(2), rs.getString(3).contains("机构同步核对") ? "机构同步核对" : "",
                        rs.getBoolean(4), rs.getInt(5)), templateId);
        if (n.isEmpty()) throw ApiException.conflict("流程模板没有节点,请先在流程设计器中配置");
        return n;
    }

    private static int gateIdx(List<Node> nodes) {
        return nodes.stream().filter(Node::gate).map(Node::idx).findFirst().orElse(5);
    }

    private static int rejectIdx(List<Node> nodes) {
        return nodes.stream().filter(n -> "分析成稿".equals(n.name())).map(Node::idx).findFirst().orElse(3);
    }

    private static int lastIdx(List<Node> nodes) {
        return nodes.get(nodes.size() - 1).idx();
    }

    private static Optional<Node> node(List<Node> nodes, int idx) {
        return nodes.stream().filter(n -> n.idx() == idx).findFirst();
    }

    private static String stepLabel(List<Node> nodes, int step) {
        if (step >= lastIdx(nodes)) return "已归档";
        return "第 " + step + " 步 · " + node(nodes, step).map(Node::name).orElse("—");
    }

    private String dueLabel(FlowRow f, boolean archived) {
        if (archived || f.dueDate() == null) return "";
        long d = ChronoUnit.DAYS.between(LocalDate.now(zone), f.dueDate());
        return f.duePrefix() + (d < 0 ? "超期 " + (-d) + " 天" : d == 0 ? "今日到期" : "剩 " + d + " 天");
    }

    // ---------------------------------------------------------------- 待办

    /**
     * 左侧待办：六类发布流程 + 对标档位切换（A13 发起、待召集人审批）。
     * 先把 A5 新生成的报告草稿登记为「月告知」流程实例（只读 report_draft，幂等）。
     */
    @Transactional
    public List<TodoGroup> todos() {
        syncDrafts();
        Map<Long, List<Node>> tpl = new HashMap<>();
        List<FlowRow> flows = jdbc.query("select " + FLOW_COLS + " from pub_flow order by sort, id", (rs, i) -> mapFlow(rs));
        List<TodoGroup> out = new ArrayList<>();
        for (String g : GROUPS) {
            List<TodoItem> items = new ArrayList<>();
            for (FlowRow f : flows) {
                if (!g.equals(f.kind())) continue;
                List<Node> ns = tpl.computeIfAbsent(f.templateId(), this::nodes);
                boolean archived = f.step() >= lastIdx(ns);
                String tone = archived ? "muted" : f.step() == gateIdx(ns) ? "danger" : "primary";
                String label = archived ? "已归档" : "第" + f.step() + "步 · " + node(ns, f.step()).map(Node::name).orElse("—");
                String due = dueLabel(f, archived);
                boolean overdue = due.contains("超期");
                items.add(new TodoItem("flow", f.id(), f.name(), label, tone, due, overdue ? "danger" : "muted",
                        f.draftId() != null ? "A5 草稿" : null));
            }
            out.add(new TodoGroup(g, items));
        }
        List<TodoItem> tiers = jdbc.query("""
                select r.id, r.indicator, r.from_tier, r.to_tier, u.name from tier_change_request r
                join app_user u on u.id = r.requested_by where r.status = 'PENDING' order by r.created_at, r.id""",
                (rs, i) -> new TodoItem("tier", rs.getLong(1), rs.getString(2) + " 档位切换",
                        TierApprovalService.tierName(rs.getInt(3)) + " → " + TierApprovalService.tierName(rs.getInt(4)), "warning",
                        rs.getString(5) + " 申请", "muted", "A13"));
        out.add(new TodoGroup(TierApprovalService.GROUP, tiers));
        out.add(new TodoGroup(IndicatorApprovalService.GROUP, indicatorApprovals.todos()));
        return out;
    }

    /** A5「生成报告并提交发布工作流」产生的草稿 → 「月告知」流程实例，进入草稿记录的步（第 3 步「分析成稿」）。 */
    private void syncDrafts() {
        List<Map<String, Object>> drafts = jdbc.queryForList("""
                select d.id, d.preset, d.period, d.block_ids, d.flow_step, d.created_at, u.name as creator
                from report_draft d join app_user u on u.id = d.created_by
                where not exists (select 1 from pub_flow f where f.report_draft_id = d.id) order by d.id""");
        if (drafts.isEmpty()) return;
        Long tplId = jdbc.queryForList("select id from flow_template where kind = '月告知'", Long.class).stream().findFirst()
                .orElseThrow(() -> ApiException.conflict("缺少「月告知」流程模板"));
        List<Node> ns = nodes(tplId);
        Map<String, Object> all = allScope();
        for (Map<String, Object> d : drafts) {
            long draftId = ((Number) d.get("id")).longValue();
            String preset = (String) d.get("preset");
            String period = periodLabel((String) d.get("period"));
            int step = ((Number) d.get("flow_step")).intValue();
            Integer[] blocks = blockIds(d.get("block_ids"));
            int days = node(ns, step).map(Node::days).orElse(5);
            List<Long> ids = jdbc.queryForList("""
                    insert into pub_flow (kind, template_id, name, subject, package_version, step, due_date, scope, report_draft_id, sort)
                    values ('月告知', ?, ?, ?, 'v1', ?, current_date + ?, ?::jsonb, ?, ?)
                    on conflict (report_draft_id) do nothing returning id""", Long.class,
                    tplId, Texts.truncate(period + " " + preset + " · 草稿 #" + draftId, 64), Texts.truncate(period + " " + preset, 64),
                    step, days, json(all), draftId, 100 + draftId);
            if (ids.isEmpty()) continue;
            long fid = ids.get(0);
            Integer height = jdbc.queryForObject("select coalesce(sum(height), 0) from report_block where id = any(?)", Integer.class,
                    (Object) blocks);
            int pages = Math.max(1, Math.round((height == null ? 0 : height) / 90f));
            Set<Integer> has = new HashSet<>(Arrays.asList(blocks));
            List<Object[]> items = new ArrayList<>();
            items.add(new Object[]{"指标集", "12 项 · 必选 8 / 增选 4", false});
            items.add(new Object[]{preset, "v1 · " + blocks.length + " 个区块 · 约 " + pages + " 页", false});
            if (has.contains(9)) items.add(new Object[]{"解读", "待人工审定", false});
            if (has.contains(10)) items.add(new Object[]{"常见问答", "随报告区块生成", false});
            if (has.contains(11)) items.add(new Object[]{"方法卡", "随报告区块生成", false});
            items.add(new Object[]{"单病例费用明细", "病例级 · 仅内部", true});
            items.add(new Object[]{"参保人就医轨迹", "个人级 · 仅内部", true});
            for (int i = 0; i < items.size(); i++) {
                Object[] it = items.get(i);
                jdbc.update("insert into pub_package_item (flow_id, sort, name, detail, internal) values (?,?,?,?,?)", fid, i + 1, it[0], it[1], it[2]);
            }
            String at = String.valueOf(d.get("created_at"));
            jdbc.update("insert into pub_log (flow_id, at, who, what) values (?, ?::timestamptz, ?, ?)", fid, at,
                    d.get("creator") + " · A5 报告模板",
                    "生成报告草稿「" + preset + "」(" + blocks.length + " 个区块),进入第 " + step + " 步「"
                            + node(ns, step).map(Node::name).orElse("分析成稿") + "」");
        }
    }

    private static Integer[] blockIds(Object o) {
        try {
            return o instanceof Array a ? (Integer[]) a.getArray() : new Integer[0];
        } catch (SQLException e) {
            return new Integer[0];
        }
    }

    private static String periodLabel(String p) {
        if (p == null || !p.matches("\\d{4}-\\d{2}")) return String.valueOf(p);
        return p.substring(0, 4) + "年" + Integer.parseInt(p.substring(5)) + "月";
    }

    // ---------------------------------------------------------------- 定向范围

    public Map<String, List<Option>> options() {
        Map<String, List<Option>> m = new LinkedHashMap<>();
        for (String d : DIMS) m.put(d, new ArrayList<>());
        jdbc.query("select dim, value, label from pub_scope_dim order by dim, sort",
                rs -> { m.get(rs.getString(1)).add(new Option(rs.getString(2), rs.getString(3))); });
        return m;
    }

    private Map<String, Object> allScope() {
        Map<String, List<Option>> o = options();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("tiers", o.get("tiers").stream().map(Option::value).toList());
        s.put("districts", o.get("districts").stream().map(Option::value).toList());
        s.put("batch", o.get("batch").get(0).value());
        s.put("alliance", o.get("alliance").get(0).value());
        s.put("group", o.get("group").get(0).value());
        return s;
    }

    /** 覆盖机构数与名单：引擎计算。 */
    public Map<String, Object> coverage(Scope scope) {
        List<Map<String, Object>> orgs = jdbc.query("select name, tier, district, batch, alliance, groups from pub_org order by sort", (rs, i) -> {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("name", rs.getString(1));
            o.put("tier", rs.getString(2));
            o.put("district", rs.getString(3));
            o.put("batch", rs.getString(4));
            o.put("alliance", rs.getString(5));
            o.put("groups", Arrays.asList((String[]) rs.getArray(6).getArray()));
            return o;
        });
        return engine.post("/v1/publish/coverage", Map.of("orgs", orgs, "scope", scope));
    }

    private static int count(Map<String, Object> cov) {
        return cov.get("count") instanceof Number n ? n.intValue() : 0;
    }

    private Scope validScope(Scope s) {
        if (s == null) throw ApiException.validation("缺少定向范围");
        Map<String, List<Option>> o = options();
        Set<String> tiers = values(o, "tiers"), dists = values(o, "districts");
        List<String> t = s.tiers() == null ? List.of() : s.tiers().stream().distinct().toList();
        List<String> d = s.districts() == null ? List.of() : s.districts().stream().distinct().toList();
        if (!tiers.containsAll(t)) throw ApiException.validation("等级取值无效");
        if (!dists.containsAll(d)) throw ApiException.validation("县区取值无效");
        // 多选按界面顺序保存
        t = o.get("tiers").stream().map(Option::value).filter(t::contains).toList();
        d = o.get("districts").stream().map(Option::value).filter(d::contains).toList();
        for (String[] kv : new String[][]{{"batch", s.batch()}, {"alliance", s.alliance()}, {"group", s.group()}}) {
            if (kv[1] == null || !values(o, kv[0]).contains(kv[1])) throw ApiException.validation("定向范围取值无效:" + kv[0]);
        }
        return new Scope(t, d, s.batch(), s.alliance(), s.group());
    }

    private static Set<String> values(Map<String, List<Option>> o, String dim) {
        Set<String> s = new HashSet<>();
        o.get(dim).forEach(x -> s.add(x.value()));
        return s;
    }

    @Transactional
    public Map<String, Object> updateScope(long id, Scope req) {
        FlowRow f = flow(id, true);
        List<Node> ns = nodes(f.templateId());
        if (f.step() > gateIdx(ns)) throw ApiException.conflict("已批准发布,定向范围不可修改;如需调整请发起更正");
        Scope s = validScope(req);
        jdbc.update("update pub_flow set scope = ?::jsonb where id = ?", json(s), id);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("scope", s);
        r.put("coverage", coverage(s));
        return r;
    }

    // ---------------------------------------------------------------- 详情

    public FlowDetail detail(long id) {
        FlowRow f = flow(id, false);
        List<Node> ns = nodes(f.templateId());
        int gate = gateIdx(ns);
        boolean archived = f.step() >= lastIdx(ns);
        String next = f.step() < gate ? node(ns, f.step() + 1).map(Node::name).orElse(null) : null;
        FlowInfo info = new FlowInfo(f.id(), f.kind(), f.name(), f.subject(), f.packageVersion(), f.step(), stepLabel(ns, f.step()), archived,
                gate, rejectIdx(ns), f.action(), f.draftId(), f.step() > gate, next);

        List<PackageItem> items = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        jdbc.query("select name, detail, internal from pub_package_item where flow_id = ? order by sort", rs -> {
            if (rs.getBoolean(3)) excluded.add(rs.getString(1));
            else items.add(new PackageItem(rs.getString(1), rs.getString(2)));
        }, id);

        Map<String, Object> cov;
        try {
            cov = coverage(f.scope());
        } catch (ApiException e) {
            cov = null;   // 引擎不可用：覆盖数显示「—」，批准时再次计算
        }

        Integer open = jdbc.queryForObject("select count(*) from opinion_ticket where category = '核对期异议' and status <> 'DONE'", Integer.class);
        boolean convener = Roles.CONVENER.equals(CurrentUser.get().role());
        return new FlowDetail(info, ns, new Package(items, excluded), f.scope(), options(), cov, logs(id), corrections(f, ns),
                open == null ? 0 : open, convener, advance(f, ns, cov));
    }

    private List<Log> logs(long flowId) {
        return jdbc.query("select at, who, what from pub_log where flow_id = ? order by at, id",
                (rs, i) -> new Log(rs.getObject(1, OffsetDateTime.class).atZoneSameInstant(zone).format(LOG_TIME), rs.getString(2), rs.getString(3)),
                flowId);
    }

    private Corrections corrections(FlowRow f, List<Node> ns) {
        List<Release> rel = jdbc.query("""
                select version, published_on, signed, total, status, note from pub_release where subject = ? order by version""",
                (rs, i) -> new Release(rs.getInt(1), f.subject() + " v" + rs.getInt(1), rs.getObject(2, LocalDate.class).format(DAY),
                        rs.getInt(3), rs.getInt(4), rs.getString(5), rs.getString(6)), f.subject());
        String action = f.action(), expl = f.explanation();
        if (expl == null) {
            // 普通流程：展示同一发布物最近一次更正 / 撤回的说明
            List<Map<String, Object>> c = jdbc.queryForList("""
                    select action, explanation from pub_flow where subject = ? and action is not null and explanation is not null
                    order by created_at desc, id desc limit 1""", f.subject());
            if (!c.isEmpty()) {
                action = (String) c.get(0).get("action");
                expl = (String) c.get(0).get("explanation");
            }
        }
        boolean hasCurrent = rel.stream().anyMatch(r -> "CURRENT".equals(r.status()));
        return new Corrections(f.subject(), rel, action, expl, hasCurrent && openCorrection(f.subject()) == null);
    }

    /** 同一发布物进行中（尚未批准）的更正 / 撤回流程。 */
    private Long openCorrection(String subject) {
        List<Long> r = jdbc.queryForList("""
                select f.id from pub_flow f where f.subject = ? and f.action is not null
                and f.step <= coalesce((select min(n.idx) from flow_node n where n.template_id = f.template_id and n.gate), 5)""",
                Long.class, subject);
        return r.isEmpty() ? null : r.get(0);
    }

    public List<AudienceVersion> audienceVersions() {
        Map<Integer, List<AudienceLine>> lines = new HashMap<>();
        jdbc.query("select version_id, label, value, tone from pub_audience_line order by version_id, sort",
                rs -> { lines.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(new AudienceLine(rs.getString(2), rs.getString(3), rs.getString(4))); });
        return jdbc.query("select id, title, subtitle, note, note_tone from pub_audience_version order by id",
                (rs, i) -> new AudienceVersion(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        lines.getOrDefault(rs.getInt(1), List.of())));
    }

    // ================================================================ 动作

    private void log(long flowId, String who, String what) {
        jdbc.update("insert into pub_log (flow_id, who, what) values (?, ?, ?)", flowId, Texts.truncate(who, 48), Texts.truncate(what, 256));
    }

    private void moveTo(long flowId, List<Node> ns, int step) {
        int days = node(ns, step).map(Node::days).orElse(5);
        jdbc.update("update pub_flow set step = ?, due_date = current_date + ?, due_prefix = '' where id = ?", step, days, flowId);
    }

    private static String withOpinion(String head, String opinion) {
        return Texts.blank(opinion) ? head : head + ":" + opinion.trim();
    }

    /** 提交至下一环节（第 5 步之前）；召集人与行政管理组均可。 */
    @Transactional
    public Map<String, Object> submit(long id) {
        AuthUser u = CurrentUser.get();
        FlowRow f = flow(id, true);
        List<Node> ns = nodes(f.templateId());
        int gate = gateIdx(ns);
        if (f.step() >= gate) throw ApiException.conflict(f.step() == gate ? "当前为召集人审批环节,请在下方批准或驳回" : "已批准发布,后续环节由机构签收与意见处理推进");
        Node cur = node(ns, f.step()).orElseThrow();
        Node next = ns.stream().filter(n -> n.idx() > f.step()).findFirst().orElseThrow();
        moveTo(id, ns, next.idx());
        String what = next.gate() ? "完成「" + cur.name() + "」,提交召集人审批" : "完成「" + cur.name() + "」,提交「" + next.name() + "」";
        log(id, ConvenerGuard.who(u), what);
        if (next.gate()) log(id, "系统", "进入第 " + next.idx() + " 步:" + next.name());
        return Map.of("message", next.gate() ? "已提交召集人审批" : "已提交至第 " + next.idx() + " 步「" + next.name() + "」");
    }

    public record DecisionReq(String opinion) {}

    /** 批准发布：整包放行，进入「定向发布」，按定向范围推送；只限召集人。 */
    @Transactional
    public Map<String, Object> approve(long id, DecisionReq req) {
        FlowRow probe = flow(id, false);
        AuthUser u = guard.require("A8 批准发布「" + probe.name() + "」");
        FlowRow f = flow(id, true);
        List<Node> ns = nodes(f.templateId());
        int gate = gateIdx(ns);
        if (f.step() != gate) throw ApiException.conflict("当前环节为「" + stepLabel(ns, f.step()) + "」,仅第 " + gate + " 步可审批");
        Map<String, Object> cov = coverage(f.scope());
        int n = count(cov);
        if (n == 0) throw ApiException.validation("定向范围未覆盖任何机构,请调整后再批准");
        String opinion = req == null ? null : Texts.truncate(Texts.blank(req.opinion()) ? null : req.opinion().trim(), 200);
        int next = ns.stream().filter(x -> x.idx() > gate).map(Node::idx).findFirst().orElse(gate + 1);
        moveTo(id, ns, next);
        log(id, ConvenerGuard.who(u), withOpinion("批准发布", opinion));
        log(id, "系统", release(f, n));
        guard.record(u, "A8 批准发布「" + f.name() + "」· 定向 " + n + " 家机构", "批准");
        String msg = "撤回".equals(f.action()) ? "已批准撤回 · 机构端将显示“已撤回”" : "已批准 · 发布包已定向发布至 " + n + " 家机构";
        return Map.of("message", msg, "step", next);
    }

    /** 生成发布版本；更正保留原版（只读），撤回标记原版已撤回。返回系统留痕文字。 */
    private String release(FlowRow f, int n) {
        LocalDate today = LocalDate.now(zone);
        Integer max = jdbc.queryForObject("select coalesce(max(version), 0) from pub_release where subject = ?", Integer.class, f.subject());
        int ver = (max == null ? 0 : max) + 1;
        if ("撤回".equals(f.action())) {
            int rows = jdbc.update("update pub_release set status = 'WITHDRAWN' where subject = ? and status = 'CURRENT'", f.subject());
            return rows > 0 ? "已撤回「" + f.subject() + "」现行版本,原版保留只读;通知 " + n + " 家机构" : "无现行版本可撤回";
        }
        jdbc.update("update pub_release set status = 'SUPERSEDED' where subject = ? and status = 'CURRENT'", f.subject());
        jdbc.update("insert into pub_release (subject, version, flow_id, published_on, signed, total, status, note) values (?,?,?,?,0,?,'CURRENT',?)",
                f.subject(), ver, f.id(), today, n, "更正".equals(f.action()) ? "经专家组复核与召集人审批" : "经召集人审批");
        return "更正".equals(f.action())
                ? "更正版本 v" + ver + " 定向发布至 " + n + " 家机构,原版 v" + (ver - 1) + " 保留只读"
                : "定向发布至 " + n + " 家机构,签收期 5 个工作日";
    }

    /** 驳回：固定退回「分析成稿」，驳回意见必填；只限召集人。 */
    @Transactional
    public Map<String, Object> reject(long id, DecisionReq req) {
        FlowRow probe = flow(id, false);
        AuthUser u = guard.require("A8 驳回「" + probe.name() + "」");
        if (req == null || Texts.blank(req.opinion())) throw ApiException.validation("请填写驳回意见");
        FlowRow f = flow(id, true);
        List<Node> ns = nodes(f.templateId());
        int gate = gateIdx(ns);
        if (f.step() != gate) throw ApiException.conflict("当前环节为「" + stepLabel(ns, f.step()) + "」,仅第 " + gate + " 步可驳回");
        int back = rejectIdx(ns);
        String target = node(ns, back).map(Node::name).orElse("分析成稿");
        moveTo(id, ns, back);
        log(id, ConvenerGuard.who(u), withOpinion("驳回至「" + target + "」", Texts.truncate(req.opinion().trim(), 200)));
        guard.record(u, "A8 驳回「" + f.name() + "」至「" + target + "」", "驳回");
        return Map.of("message", "已驳回至第 " + back + " 步「" + target + "」", "step", back);
    }

    public record CorrectionReq(String action, String reason) {}

    /** 发起更正 / 撤回：新建「更正与撤回」流程，重新经过专家组审核与召集人审批；原版本保留。 */
    @Transactional
    public Map<String, Object> correct(long id, CorrectionReq req) {
        AuthUser u = CurrentUser.get();
        if (req == null || !("更正".equals(req.action()) || "撤回".equals(req.action()))) throw ApiException.validation("请选择更正或撤回");
        if (Texts.blank(req.reason())) throw ApiException.validation("请填写" + req.action() + "说明");
        FlowRow f = flow(id, true);
        Integer cur = jdbc.queryForObject("select max(version) from pub_release where subject = ? and status = 'CURRENT'", Integer.class, f.subject());
        if (cur == null) throw ApiException.conflict("「" + f.subject() + "」尚无现行发布版本,不能" + req.action());
        if (openCorrection(f.subject()) != null) throw ApiException.conflict("「" + f.subject() + "」已有进行中的更正 / 撤回流程");
        Long tplId = jdbc.queryForList("select id from flow_template where kind = '更正与撤回'", Long.class).stream().findFirst()
                .orElseThrow(() -> ApiException.conflict("缺少「更正与撤回」流程模板"));
        List<Node> ns = nodes(tplId);
        int start = rejectIdx(ns);
        String reason = Texts.truncate(req.reason().trim(), 500);
        String ver = "更正".equals(req.action()) ? "v" + (cur + 1) : "v" + cur;
        Long nid = jdbc.queryForObject("""
                insert into pub_flow (kind, template_id, name, subject, package_version, step, due_date, scope, action, explanation, sort)
                values ('更正与撤回', ?, ?, ?, ?, ?, current_date + ?, ?::jsonb, ?, ?, 1000) returning id""", Long.class,
                tplId, Texts.truncate(f.subject() + " " + req.action(), 64), f.subject(), ver, start,
                node(ns, start).map(Node::days).orElse(5), json(f.scope()), req.action(), reason);
        jdbc.update("""
                insert into pub_package_item (flow_id, sort, name, detail, internal)
                select ?, sort, name, detail, internal from pub_package_item where flow_id = ?""", nid, f.id());
        log(nid, ConvenerGuard.who(u), "发起" + req.action() + ":" + reason);
        log(nid, "系统", "原版 v" + cur + " 保留只读;进入第 " + start + " 步「" + node(ns, start).map(Node::name).orElse("分析成稿") + "」");
        return Map.of("id", nid, "message", "已发起" + req.action() + "流程,将重新经过专家组审核与召集人审批");
    }

    // ================================================================ 批准后的推进(第 6 步起)

    /** 该流程最近一次生成的发布版本(撤回流程没有新版本)。 */
    private Optional<Map<String, Object>> releaseOf(long flowId) {
        return jdbc.queryForList("select id, published_on, total, signed from pub_release where flow_id = ? order by version desc limit 1", flowId)
                .stream().findFirst();
    }

    /** 已签收 / 已生成报告数:报告已生成则取 B4 实时数据,否则取发布版本登记值。 */
    private int[] signStats(Map<String, Object> rel) {
        long rid = ((Number) rel.get("id")).longValue();
        Map<String, Object> c = jdbc.queryForMap("select count(*) as n, count(*) filter (where status = 'SIGNED') as s from pr_report where release_id = ?", rid);
        int n = ((Number) c.get("n")).intValue();
        int total = ((Number) rel.get("total")).intValue();
        return n == 0 ? new int[]{((Number) rel.get("signed")).intValue(), total, 0} : new int[]{((Number) c.get("s")).intValue(), total, n};
    }

    /** 发布后收到的意见(A10 工单,B5 提交)及其中未答复数。 */
    private int[] opinionStats(Map<String, Object> rel) {
        LocalDate since = (LocalDate) (rel.get("published_on") instanceof java.sql.Date d ? d.toLocalDate() : rel.get("published_on"));
        Map<String, Object> c = jdbc.queryForMap("select count(*) as n, count(*) filter (where status <> 'DONE') as o from opinion_ticket where created_at >= ?", since);
        return new int[]{((Number) c.get("n")).intValue(), ((Number) c.get("o")).intValue()};
    }

    private Advance advance(FlowRow f, List<Node> ns, Map<String, Object> cov) {
        int gate = gateIdx(ns);
        if (f.step() <= gate || f.step() >= lastIdx(ns)) return null;
        Node cur = node(ns, f.step()).orElseThrow();
        Node next = ns.stream().filter(n -> n.idx() > f.step()).findFirst().orElseThrow();
        Optional<Map<String, Object>> rel = releaseOf(f.id());
        Integer signed = null, total = null;
        int ops = 0, open = 0;
        if (rel.isPresent()) {
            int[] st = signStats(rel.get());
            signed = st[0];
            total = st[1];
            int[] o = opinionStats(rel.get());
            ops = o[0];
            open = o[1];
        }
        boolean withdraw = "撤回".equals(f.action());
        boolean allowed = true;
        String blocked = null, label, hint;
        switch (cur.name()) {
            case "定向发布" -> {
                int n = cov == null ? (total == null ? 0 : total) : count(cov);
                label = withdraw ? "送达撤回通知" : "执行定向发布";
                hint = withdraw ? "向 " + n + " 家机构送达撤回通知,原版本已标记撤回、保留只读。"
                        : "按定向范围向 " + n + " 家机构生成发布报告(嵌入各机构实名水印编号),机构在 B4 报告中心 / 移动端签收。";
            }
            case "签收查阅" -> {
                label = "结束签收期,进入「" + next.name() + "」";
                hint = "已签收 " + (signed == null ? "—" : signed) + "/" + (total == null ? "—" : total) + " 家"
                        + (signed != null && total != null && signed < total ? ",未签收 " + (total - signed) + " 家(逾期由行政管理组催办)。" : "。");
            }
            case "意见申诉" -> {
                label = "结束意见申诉期,进入「" + next.name() + "」";
                hint = "发布后共收到意见 " + ops + " 条,其中待答复 " + open + " 条(A10 意见与申诉管理)。";
            }
            case "答复整改" -> {
                label = "完成答复整改,归档复盘";
                hint = "发布后收到的意见须全部答复后方可归档;当前待答复 " + open + " 条。";
                if (open > 0) {
                    allowed = false;
                    blocked = "还有 " + open + " 条意见未答复,请在 A10 答复后再归档";
                }
            }
            default -> {
                label = "完成「" + cur.name() + "」,进入「" + next.name() + "」";
                hint = "";
            }
        }
        return new Advance(next.idx(), next.name(), label, hint, allowed, blocked, signed, total, ops, open);
    }

    private static String kindOfSubject(String subject, String flowKind) {
        if (subject.contains("专题") || "病种与机构专题".equals(flowKind)) return "专题报告";
        return subject.contains("体检") ? "体检报告" : "月度报告";
    }

    /** 执行定向发布:按定向范围为每家机构生成待签收报告(B4 / D1 可见);更正使原版本只读保留。返回留痕文字。 */
    private String dispatch(FlowRow f, Optional<Map<String, Object>> rel, List<PackageItem> items) {
        boolean withdraw = "撤回".equals(f.action());
        // 更正 / 撤回:原发布版本下尚未归档的机构报告改为只读保留
        jdbc.update("""
                update pr_report set status = 'OLD' where status in ('SIGN', 'CHECK', 'SIGNED') and release_id in
                (select id from pub_release where subject = ? and status in ('SUPERSEDED', 'WITHDRAWN'))""", f.subject());
        Map<String, Object> cov = coverage(f.scope());
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) cov.get("names");
        if (withdraw) return "撤回通知已送达 " + names.size() + " 家机构,原版本保留只读";
        if (rel.isEmpty()) throw ApiException.conflict("缺少发布版本,无法定向发布");
        long rid = ((Number) rel.get().get("id")).longValue();
        int ver = jdbc.queryForObject("select version from pub_release where id = ?", Integer.class, rid);
        LocalDate today = LocalDate.now(zone);
        String kind = kindOfSubject(f.subject(), f.kind());
        String title = Texts.truncate(f.subject() + ("更正".equals(f.action()) ? "(更正版 v" + ver + ")" : ""), 64);
        String contents = items.stream().map(PackageItem::name).collect(java.util.stream.Collectors.joining("、"));
        for (String org : names) {
            String wm = audit.nextWatermark();
            String body = json(List.of(
                    Map.of("h", "一、发布说明", "p", f.subject() + " 经召集人审批后定向发布至 " + org + ";本报告仅限本院查阅,页面嵌入实名水印与编号 " + wm + "。"),
                    Map.of("h", "二、发布包内容", "p", "本期发布包含:" + contents + "。仅内部指标与病例级、个人级数据已自动排除。")));
            jdbc.update("""
                    insert into pr_report (org, title, kind, published_on, pages, status, wm_no, body, sort, release_id)
                    values (?, ?, ?, ?, 12, 'SIGN', ?, ?::jsonb, (select coalesce(max(sort), 0) + 1 from pr_report where org = ?), ?)""",
                    org, title, kind, today, wm, body, org, rid);
        }
        return "定向发布 " + names.size() + " 家机构:已生成各机构发布报告,待签收(B4 / 移动端)";
    }

    /** 推进到下一环节(批准发布之后):定向发布 → 签收查阅 → 意见申诉 → 答复整改 → 归档。召集人与行政管理组均可。 */
    @Transactional
    public Map<String, Object> advance(long id) {
        AuthUser u = CurrentUser.get();
        FlowRow f = flow(id, true);
        List<Node> ns = nodes(f.templateId());
        int gate = gateIdx(ns), last = lastIdx(ns);
        if (f.step() <= gate) throw ApiException.conflict("尚未批准发布,请先在第 " + gate + " 步审批");
        if (f.step() >= last) throw ApiException.conflict("流程已归档");
        Advance a = advance(f, ns, null);
        if (!a.allowed()) throw ApiException.conflict(a.blocked());
        Node cur = node(ns, f.step()).orElseThrow();
        Node next = node(ns, a.toStep()).orElseThrow();
        Optional<Map<String, Object>> rel = releaseOf(id);
        String what;
        switch (cur.name()) {
            case "定向发布" -> {
                List<PackageItem> items = new ArrayList<>();
                jdbc.query("select name, detail from pub_package_item where flow_id = ? and not internal order by sort",
                        rs -> { items.add(new PackageItem(rs.getString(1), rs.getString(2))); }, id);
                what = dispatch(f, rel, items);
            }
            case "签收查阅" -> {
                int[] st = rel.map(this::signStats).orElse(new int[]{0, 0, 0});
                what = "签收期结束:已签收 " + st[0] + "/" + st[1] + " 家" + (st[0] < st[1] ? ",未签收 " + (st[1] - st[0]) + " 家" : "");
            }
            case "意见申诉" -> what = "意见申诉期结束:发布后共收到意见 " + a.opinions() + " 条";
            case "答复整改" -> what = "答复整改完成:本期意见均已答复";
            default -> what = "完成「" + cur.name() + "」";
        }
        moveTo(id, ns, next.idx());
        log(id, ConvenerGuard.who(u), what + (next.idx() >= last ? ",归档复盘" : ",进入「" + next.name() + "」"));
        if (next.idx() >= last) log(id, "系统", "流程已归档");
        return Map.of("message", next.idx() >= last ? "已归档" : "已进入第 " + next.idx() + " 步「" + next.name() + "」", "step", next.idx());
    }
}
