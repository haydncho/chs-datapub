package cn.ybdata.core.domain;

import cn.ybdata.core.action.ActionHandler;
import cn.ybdata.core.page.PageOverlay;
import cn.ybdata.core.security.Actor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 报告签收: B4 (web) and D1 (mobile) sign the same report rows, always <b>per institution</b>
 * (report_signoff, keyed by the signer's own organisation). Only a 定点医疗机构 identity signs for its own
 * institution — a 医保局 account cannot sign on an institution's behalf — and only reports that passed
 * 召集人审批 (未批准不外发) and target that institution. What each viewer sees is served by {@link ReportScope}.
 */
@Configuration
public class ReportDomain {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public ReportDomain(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    record Report(String id, String title, String kind, java.time.LocalDate published, String version, String status) {}

    /** a report and the publish task it is released by (null when it is not tied to the workflow) */
    record Release(Report report, Integer taskStep, List<String> institutions) {
        /** 未批准不外发: a 核对稿 goes out for 机构核对 before approval, anything else only from step 6 on */
        boolean released() {
            return taskStep == null || taskStep >= PublishRules.TARGETED_RELEASE_STEP || "check".equals(report.status());
        }

        /** the institution is in the approved 定向范围 (or the release is not targeted) */
        boolean targets(String orgName) {
            return institutions == null || institutions.isEmpty() || orgName == null || PublishRules.covers(institutions, orgName);
        }
    }

    List<Release> releases() {
        List<Report> reports = jdbc.sql("select id, title, kind, published, version, status from report order by published desc nulls last, id")
                .query((rs, i) -> new Report(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate(), rs.getString(5), rs.getString(6)))
                .list();
        List<Release> out = new ArrayList<>();
        for (Report r : reports) {
            var task = jdbc.sql("select step, scope::text from publish_task where report_id = :r and origin_task is null order by id limit 1")
                    .param("r", r.id()).query((rs, i) -> new Object[] {rs.getInt(1), rs.getString(2)}).optional();
            Integer step = task.map(t -> (Integer) t[0]).orElse(null);
            List<String> inst = new ArrayList<>();
            task.map(t -> (String) t[1]).ifPresent(s -> {
                try {
                    json.readTree(s).path("institutions").forEach(n -> inst.add(n.asText()));
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    throw new IllegalStateException(e);
                }
            });
            out.add(new Release(r, step, inst));
        }
        return out;
    }

    Optional<Release> release(String reportId) {
        return releases().stream().filter(x -> x.report().id().equals(reportId)).findFirst();
    }

    /** when the organisation signed the report, if it did */
    Optional<OffsetDateTime> signedAt(String reportId, String orgId) {
        if (orgId == null) return Optional.empty();
        return jdbc.sql("select signed_at from report_signoff where report_id = :r and org_id = :o")
                .param("r", reportId).param("o", orgId).query(OffsetDateTime.class).optional();
    }

    /** sign {@code reportId} for the caller's own institution */
    Map<String, Object> sign(String reportId, String channel) {
        Actor a = Who.requireHospital("仅定点医疗机构本院身份可签收报告,医保局账号不能代机构签收");
        Release rel = release(reportId).orElseThrow(() -> new IllegalArgumentException("报告不存在:" + reportId));
        if (!rel.released() || !rel.targets(a.orgName())) throw new IllegalArgumentException("该报告未向本院发布,不能签收");
        if (!"sign".equals(rel.report().status())) throw new IllegalArgumentException("该报告当前不需要签收");
        int n = jdbc.sql("""
                insert into report_signoff (report_id, org_id, signed_by, channel) values (:r, :o, :by, :ch)
                on conflict (report_id, org_id) do nothing""")
                .param("r", reportId).param("o", a.orgId()).param("by", PublishDomain.clip(a.name(), 32)).param("ch", channel).update();
        if (n == 0) throw new IllegalArgumentException("本院已签收该报告,无需重复签收");
        return Map.of("reportId", reportId, "org", a.orgId(), "status", "signed");
    }

    String idByTitle(String title) {
        return jdbc.sql("select id from report where title = :t").param("t", title).query(String.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("报告不存在:" + title));
    }

    /** the report a sign / feedback call refers to: {@code reportId}, else its exact {@code name} / {@code report} title */
    String reportOf(JsonNode p) {
        String id = Json.textOr(p, "reportId", "").trim();
        if (!id.isEmpty()) {
            if (jdbc.sql("select count(*) from report where id = :id").param("id", id).query(Integer.class).single() == 0) {
                throw new IllegalArgumentException("报告不存在:" + id);
            }
            return id;
        }
        String title = Json.textOr(p, "name", Json.textOr(p, "report", "")).trim();
        if (title.isEmpty()) throw new IllegalArgumentException("missing field: reportId");
        return idByTitle(title);
    }

    @Bean
    ActionHandler b4SignReport() {
        return handler("B4", "signReport", (actor, p) -> sign(reportOf(p), "web"));
    }

    @Bean
    ActionHandler d1SignReport() {
        return handler("D1", "signReport", (actor, p) -> sign(reportOf(p), "mobile"));
    }

    /**
     * B4「对本报告提意见」: the institution's comment becomes an 意见 item in A10 (feedback_item, status todo).
     * Uses feedback_item(id, kind, org_name, title, location, report, status, body).
     */
    @Bean
    ActionHandler b4SubmitFeedback() {
        return handler("B4", "submitFeedback", (actor, p) -> {
            Actor a = Who.requireHospital("仅定点医疗机构本院身份可对报告提意见");
            String reportId = reportOf(p);
            Release rel = release(reportId).orElseThrow();
            if (!rel.released() || !rel.targets(a.orgName())) throw new IllegalArgumentException("该报告未向本院发布");
            String text = Json.textOr(p, "text", "").trim();
            if (text.length() < 5) throw new IllegalArgumentException("请填写意见内容(至少 5 个字)");
            if (text.length() > 2000) throw new IllegalArgumentException("意见内容过长(最多 2000 字)");
            String section = PublishDomain.clip(Json.textOr(p, "section", "全文").trim(), 64);
            String title = PublishDomain.clip(Json.textOr(p, "title", "").trim(), 128);
            if (title.isEmpty()) title = PublishDomain.clip(section + " · " + text.replaceAll("\\s+", " "), 40);
            for (int attempt = 0; attempt < 5; attempt++) {
                int next = jdbc.sql("select greatest(coalesce(max(cast(substring(id from 4) as int)), 1000), 1000) + 1 from feedback_item where id ~ '^YJ-[0-9]+$'")
                        .query(Integer.class).single() + attempt;
                String id = "YJ-" + next;
                int n = jdbc.sql("""
                        insert into feedback_item (id, kind, org_name, title, location, report, status, body)
                        values (:id, '意见', :org, :title, :loc, :report, 'todo', :body) on conflict (id) do nothing""")
                        .param("id", id).param("org", PublishDomain.clip(a.orgName() == null ? a.orgId() : a.orgName(), 64))
                        .param("title", title).param("loc", section).param("report", PublishDomain.clip(rel.report().title(), 128))
                        .param("body", text).update();
                if (n == 1) return Map.of("id", id);
            }
            throw new IllegalStateException("意见编号分配失败,请重试");
        });
    }

    // ── tiny factories so each domain reads as a list of rules ──
    interface Body {
        Object run(String actor, JsonNode payload);
    }

    static ActionHandler handler(String page, String action, Body body) {
        return new ActionHandler() {
            public String page() { return page; }
            public String action() { return action; }
            public Object handle(String actor, JsonNode payload) { return body.run(actor, payload); }
        };
    }

    static PageOverlay overlay(String page, java.util.function.Consumer<ObjectNode> fn) {
        return new PageOverlay() {
            public String page() { return page; }
            public void apply(ObjectNode payload) { fn.accept(payload); }
        };
    }
}
