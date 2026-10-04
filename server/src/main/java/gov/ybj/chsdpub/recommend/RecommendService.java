package gov.ybj.chsdpub.recommend;

import com.fasterxml.jackson.core.type.TypeReference;
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

/**
 * 智能推荐中心（A6）：算法只给候选，人工采纳 / 修改 / 否决（可撤销），每类推荐可追溯到方法卡。
 * 得分、归因拆分、标杆分组均由引擎计算；异常推荐的机构名称仅医保局可见（本接口仅召集人 / 行政管理组可访问）。
 */
@Service
public class RecommendService {

    public static final List<String> REASONS = List.of("病例量大", "结算金额大", "趋势变化大", "基金差额大", "机构关注度高", "机构推荐集中",
            "收治结构Z分数异常");
    private static final TypeReference<List<String>> STR_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final EngineClient engine;
    private final ObjectMapper om;
    private final ZoneId zone;

    public RecommendService(JdbcTemplate jdbc, EngineClient engine, ObjectMapper om, AppProperties props) {
        this.jdbc = jdbc;
        this.engine = engine;
        this.om = om;
        this.zone = ZoneId.of(props.zone());
    }

    private <T> T read(String json, TypeReference<T> t) {
        try {
            return json == null ? null : om.readValue(json, t);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String write(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ================================================================ 选题

    public record Topic(String code, String name, int score, List<String> reasons, List<String> engineReasons, String status, boolean modified,
                        String note, String decidedBy, OffsetDateTime decidedAt) {}

    private record TopicRow(String code, String name, String period, Map<String, Integer> factors, String status, List<String> override,
                            String note, String decidedBy, OffsetDateTime decidedAt) {}

    private List<TopicRow> topicRows() {
        return jdbc.query("""
                select t.code, t.name, t.period, t.f_case, t.f_amount, t.f_trend, t.f_fund, t.f_attention, t.f_recommend, t.f_admit_z,
                       t.status, t.reasons_override::text, t.note, u.name, t.decided_at
                from rec_topic t left join app_user u on u.id = t.decided_by order by t.sort""", (rs, i) -> {
            Map<String, Integer> f = new LinkedHashMap<>();
            f.put("caseVolume", rs.getInt(4));
            f.put("amount", rs.getInt(5));
            f.put("trend", rs.getInt(6));
            f.put("fundGap", rs.getInt(7));
            f.put("attention", rs.getInt(8));
            f.put("recommend", rs.getInt(9));
            f.put("admitZ", rs.getInt(10));
            return new TopicRow(rs.getString(1), rs.getString(2), rs.getString(3), f, rs.getString(11), read(rs.getString(12), STR_LIST),
                    rs.getString(13), rs.getString(14), rs.getObject(15, OffsetDateTime.class));
        });
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> topics() {
        List<TopicRow> rows = topicRows();
        List<Map<String, Object>> in = rows.stream()
                .map(t -> Map.<String, Object>of("code", t.code(), "name", t.name(), "factors", t.factors())).toList();
        Map<String, Object> r = engine.post("/v1/indicator/topic-scores", Map.of("rows", in));
        Map<String, TopicRow> byCode = new HashMap<>();
        rows.forEach(t -> byCode.put(t.code(), t));
        List<Topic> out = new ArrayList<>();
        for (Map<String, Object> s : (List<Map<String, Object>>) r.get("rows")) {
            TopicRow t = byCode.get((String) s.get("code"));
            List<String> er = (List<String>) s.get("reasons");
            out.add(new Topic(t.code(), t.name(), ((Number) s.get("score")).intValue(), t.override() != null ? t.override() : er, er,
                    t.status(), t.override() != null || t.note() != null, t.note(), t.decidedBy(), t.decidedAt()));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("period", rows.isEmpty() ? "" : rows.get(0).period());
        m.put("rows", out);
        m.put("weights", r.get("weights"));
        m.put("reasonOptions", REASONS);
        return m;
    }

    private Topic topic(String code) {
        @SuppressWarnings("unchecked")
        List<Topic> rows = (List<Topic>) topics().get("rows");
        return rows.stream().filter(t -> t.code().equals(code)).findFirst().orElseThrow(() -> ApiException.notFound("候选病组不存在"));
    }

    private String status(String code) {
        List<String> s = jdbc.queryForList("select status from rec_topic where code = ? for update", String.class, code);
        if (s.isEmpty()) throw ApiException.notFound("候选病组不存在");
        return s.get(0);
    }

    @Transactional
    public Map<String, Object> decide(String code, String action, AuthUser u) {
        String to = switch (action == null ? "" : action) {
            case "adopt" -> "ADOPT";
            case "reject" -> "REJECT";
            default -> throw ApiException.validation("操作须为 adopt(采纳)或 reject(否决)");
        };
        if (!"CAND".equals(status(code))) throw ApiException.conflict("该候选已处理,如需变更请先撤销");
        jdbc.update("update rec_topic set status = ?, decided_by = ?, decided_at = now() where code = ?", to, u.userId(), code);
        String msg = "ADOPT".equals(to) ? "已采纳 " + code + ",进入病种专题工作台(A7)" : "已否决 " + code + ",理由将记入方法卡复核";
        return Map.of("row", topic(code), "message", msg);
    }

    @Transactional
    public Map<String, Object> undo(String code) {
        if ("CAND".equals(status(code))) throw ApiException.conflict("该候选尚未处理,无需撤销");
        jdbc.update("update rec_topic set status = 'CAND', decided_by = null, decided_at = null where code = ?", code);
        return Map.of("row", topic(code), "message", "已撤销 " + code + " 的处理,恢复为候选");
    }

    @Transactional
    public Map<String, Object> modify(String code, List<String> reasons, String note) {
        if (!"CAND".equals(status(code))) throw ApiException.conflict("仅候选状态可修改,请先撤销");
        if (reasons == null || reasons.isEmpty()) throw ApiException.validation("请至少保留一个入选理由");
        if (!REASONS.containsAll(reasons)) throw ApiException.validation("入选理由无效");
        if (note != null && note.length() > 256) throw ApiException.validation("选题范围说明不超过 256 字");
        List<String> ordered = REASONS.stream().filter(reasons::contains).toList();
        jdbc.update("update rec_topic set reasons_override = ?::jsonb, note = ? where code = ?", write(ordered),
                Texts.blank(note) ? null : note.trim(), code);
        return Map.of("row", topic(code), "message", "已修改 " + code + " 的入选理由与选题范围");
    }

    // ================================================================ 归因

    @SuppressWarnings("unchecked")
    public Map<String, Object> attribution() {
        List<Map<String, Object>> rows = jdbc.query("""
                select a.code, a.name, a.patient_diff, a.behavior_diff, a.behaviors::text, a.comorbidity_pct, a.cases, a.qc_org, q.list_qc_pct
                from rec_attribution a left join org_quality q on q.org = a.qc_org order by a.sort""", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", rs.getString(1));
            m.put("name", rs.getString(2));
            m.put("patientDiff", rs.getInt(3));
            m.put("behaviorDiff", rs.getInt(4));
            m.put("behaviors", read(rs.getString(5), MAP_LIST));
            m.put("comorbidityPct", rs.getBigDecimal(6));
            m.put("cases", rs.getInt(7));
            if (rs.getString(8) != null && rs.getBigDecimal(9) != null) {
                m.put("lowQcOrg", rs.getString(8));
                m.put("lowQcPct", rs.getBigDecimal(9));
            }
            return m;
        });
        return engine.post("/v1/indicator/attribution", Map.of("rows", rows));
    }

    // ================================================================ 标杆

    public Map<String, Object> benchmark(int threshold) {
        if (threshold < 60 || threshold > 90 || threshold % 5 != 0) throw ApiException.validation("偏离阈值须为 P60–P90,步长 5");
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        jdbc.query("select name from ind_peer_group order by sort", rs -> {
            groups.put(rs.getString(1), new ArrayList<>());
        });
        jdbc.query("select o.peer_group, b.avg_cost, b.qc_pct from rec_bench_org b join ind_org o on o.name = b.org order by o.sort", rs -> {
            groups.get(rs.getString(1)).add(Map.of("avgCost", rs.getInt(2), "qcPct", rs.getBigDecimal(3)));
        });
        List<Map<String, Object>> gs = new ArrayList<>();
        groups.forEach((k, v) -> gs.add(Map.of("name", k, "orgs", v)));
        return engine.post("/v1/indicator/benchmark", Map.of("threshold", threshold, "groups", gs));
    }

    // ================================================================ 异常

    public record Anomaly(long id, String rule, String org, String drg, String value, String level, String letterNo, String letterBy,
                          OffsetDateTime letterAt) {}

    public List<Anomaly> anomalies() {
        return jdbc.query("""
                select a.id, a.rule_name, a.org, a.drg_group, a.trigger_value, a.level, a.letter_no, u.name, a.letter_at
                from rec_anomaly a left join app_user u on u.id = a.letter_by order by a.sort""",
                (rs, i) -> new Anomaly(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getString(8), rs.getObject(9, OffsetDateTime.class)));
    }

    @Transactional
    public Map<String, Object> letter(long id, AuthUser u) {
        List<String> cur = jdbc.queryForList("select coalesce(letter_no, '') from rec_anomaly where id = ? for update", String.class, id);
        if (cur.isEmpty()) throw ApiException.notFound("异常推荐不存在");
        if (!cur.get(0).isEmpty()) throw ApiException.conflict("已生成提醒函草稿 " + cur.get(0));
        Long n = jdbc.queryForObject("select nextval('rec_letter_seq')", Long.class);
        String no = String.format("TX-%d-%04d", LocalDate.now(zone).getYear(), n);
        jdbc.update("update rec_anomaly set letter_no = ?, letter_by = ?, letter_at = now() where id = ?", no, u.userId(), id);
        Anomaly a = anomalies().stream().filter(x -> x.id() == id).findFirst().orElseThrow();
        return Map.of("row", a, "message", "已生成提醒函草稿 " + no + ",进入发布工作流「预警提醒函」");
    }

    // ================================================================ 呈现 / 方法卡

    public record Presentation(String indicator, String chart, String reason) {}

    public List<Presentation> presentations() {
        return jdbc.query("select indicator, chart, reason from rec_presentation order by sort",
                (rs, i) -> new Presentation(rs.getString(1), rs.getString(2), rs.getString(3)));
    }

    public record MethodCard(String tab, String title, List<List<String>> rows) {}

    public List<MethodCard> methods() {
        TypeReference<List<List<String>>> t = new TypeReference<>() {};
        return jdbc.query("select tab, title, rows::text from rec_method order by case tab when 'topic' then 1 when 'attr' then 2 when 'bench' then 3 when 'anom' then 4 else 5 end",
                (rs, i) -> new MethodCard(rs.getString(1), rs.getString(2), read(rs.getString(3), t)));
    }
}
