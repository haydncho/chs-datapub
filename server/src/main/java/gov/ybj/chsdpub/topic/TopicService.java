package gov.ybj.chsdpub.topic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.ybj.chsdpub.auth.AuthUser;
import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.engine.EngineClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * 病种专题工作台（A7）：七段式结构，文稿区为引擎初稿，逐段人工审定；七段全部审定后才能提交机构核对与专家组审核（流程 2）。
 * 受控分析环境：只输出聚合结果，不含病例级字段。
 */
@Service
public class TopicService {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final EngineClient engine;
    private final ObjectMapper om;

    public TopicService(JdbcTemplate jdbc, EngineClient engine, ObjectMapper om) {
        this.jdbc = jdbc;
        this.engine = engine;
        this.om = om;
    }

    public record Section(int idx, String name, String draft, int draftVersion, boolean approved, String approvedBy,
                          OffsetDateTime approvedAt) {}

    public record Benchmark(String metric, String bench, String deviant, String gap) {}

    public record Topic(String code, String name, String period, String source, Map<String, Object> overview,
                        Map<String, Object> costMix, Map<String, Object> behaviors, Map<String, Object> attribution,
                        Map<String, Object> waterfall, List<Benchmark> benchmark, Map<String, Object> optimization,
                        Map<String, Object> suggestions, List<Section> sections, int approvedCount, boolean submitted) {}

    private Map<String, Object> json(String s) {
        try {
            return om.readValue(s, MAP);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> row(String code) {
        List<Map<String, Object>> r = jdbc.queryForList("""
                select code, name, period, source, overview::text ov, cost_mix::text cm, attribution::text at,
                       optimization::text op, suggestions::text sg, submitted_at from topic where code = ?""", code);
        if (r.isEmpty()) throw ApiException.notFound("专题 " + code + " 不存在");
        return r.get(0);
    }

    private List<Map<String, Object>> behaviorRows(String code) {
        return jdbc.query("select name, rate_pct, with_avg, without_avg from topic_behavior where topic_code = ? order by sort",
                (rs, i) -> Map.<String, Object>of("name", rs.getString(1), "ratePct", rs.getBigDecimal(2), "withAvg", rs.getInt(3),
                        "withoutAvg", rs.getInt(4)), code);
    }

    private List<Benchmark> benchmark(String code) {
        return jdbc.query("select metric, bench, deviant, gap from topic_benchmark where topic_code = ? order by sort",
                (rs, i) -> new Benchmark(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), code);
    }

    private List<Section> sections(String code) {
        return jdbc.query("""
                select s.idx, s.name, s.draft, s.draft_version, s.approved_at is not null, u.name, s.approved_at
                from topic_section s left join app_user u on u.id = s.approved_by where s.topic_code = ? order by s.idx""",
                (rs, i) -> new Section(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBoolean(5),
                        rs.getString(6), rs.getObject(7, OffsetDateTime.class)), code);
    }

    public Topic get(String code) {
        Map<String, Object> t = row(code);
        Map<String, Object> attribution = json((String) t.get("at"));
        Map<String, Object> behaviors = engine.post("/v1/topic/behaviors", Map.of("rows", behaviorRows(code)));
        Map<String, Object> waterfall = engine.post("/v1/topic/waterfall", Map.of("cityMean", attribution.get("cityMean"),
                "patientDiff", attribution.get("patientDiff"), "behaviorDiff", attribution.get("behaviorDiff")));
        List<Section> secs = sections(code);
        return new Topic(code, (String) t.get("name"), (String) t.get("period"), (String) t.get("source"), json((String) t.get("ov")),
                json((String) t.get("cm")), behaviors, attribution, waterfall, benchmark(code), json((String) t.get("op")),
                json((String) t.get("sg")), secs, (int) secs.stream().filter(Section::approved).count(), t.get("submitted_at") != null);
    }

    private Section section(String code, int idx) {
        return sections(code).stream().filter(s -> s.idx() == idx).findFirst().orElseThrow(() -> ApiException.notFound("段落不存在"));
    }

    private void requireOpen(String code) {
        if (row(code).get("submitted_at") != null) throw ApiException.conflict("专题已提交审核,文稿已锁定");
    }

    @Transactional
    public Section approve(String code, int idx, AuthUser u) {
        requireOpen(code);
        section(code, idx);
        jdbc.update("update topic_section set approved_by = ?, approved_at = now() where topic_code = ? and idx = ?", u.userId(), code, idx);
        return section(code, idx);
    }

    /** 按最新数据重新生成初稿：保留历史版本号递增，并撤销该段审定。 */
    @Transactional
    @SuppressWarnings("unchecked")
    public Section regenerate(String code, int idx) {
        requireOpen(code);
        section(code, idx);
        Topic t = get(code);
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("code", t.code());
        facts.put("overview", t.overview());
        facts.put("costMix", t.costMix());
        facts.put("behaviors", t.behaviors().get("rows"));
        facts.put("attribution", t.attribution());
        facts.put("benchmark", t.benchmark());
        facts.put("optimization", t.optimization());
        Map<String, Object> r = engine.post("/v1/topic/draft", Map.of("section", idx, "facts", facts));
        jdbc.update("""
                update topic_section set draft = ?, draft_version = draft_version + 1, approved_by = null, approved_at = null
                where topic_code = ? and idx = ?""", r.get("text"), code, idx);
        return section(code, idx);
    }

    @Transactional
    public Map<String, Object> submit(String code, AuthUser u) {
        requireOpen(code);
        long pending = sections(code).stream().filter(s -> !s.approved()).count();
        if (pending > 0) throw ApiException.conflict("还有 " + pending + " 段未审定,不能提交");
        jdbc.update("update topic set submitted_at = now(), submitted_by = ? where code = ?", u.userId(), code);
        Object orgs = json((String) row(code).get("ov")).get("orgs");
        return Map.of("message", "已提交:机构核对(收治 " + code + " 的 " + orgs + " 家)与专家组审核同步进行");
    }
}
