package cn.ybdata.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A3–A7 workbench actions are persisted (page_state, V13) and merged back into the page read model;
 * refusals (validation, duplicates, role limits) are 4xx and change nothing.
 *   YB_IT_DB_URL=jdbc:postgresql://localhost:5432/ybdata_test mvn verify -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "YB_IT_DB_URL", matches = ".+")
class WorkbenchIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("YB_IT_DB_URL"));
        DevHeader.enable(r);
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcClient jdbc;

    private ResponseEntity<Map> post(String user, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.set(DevHeader.HEADER, user);
        return http.exchange("/api/v1/actions/" + path, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
    }

    private ResponseEntity<Map> post(String path, Object body) {
        return post("chenzy", path, body);
    }

    private int status(String path, Object body) {
        return post(path, body).getStatusCode().value();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> result(ResponseEntity<Map> r) {
        assertThat(r.getStatusCode().is2xxSuccessful()).as(String.valueOf(r.getBody())).isTrue();
        return (Map<String, Object>) r.getBody().get("result");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> page(String code) {
        HttpHeaders h = new HttpHeaders();
        h.set(DevHeader.HEADER, "chenzy");
        return http.exchange("/api/v1/pages/" + code, HttpMethod.GET, new HttpEntity<>(h), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> find(Object list, String key, String value) {
        return ((List<Map<String, Object>>) list).stream().filter(m -> value.equals(m.get(key))).findFirst().orElse(null);
    }

    @BeforeEach
    @AfterEach
    void reset() {
        for (String p : List.of("A3", "A4", "A5", "A7")) post(p + "/resetDemo", Map.of());
    }

    @Test
    void a3PullQualityCheckAndReportDraftPersist() {
        String period = (String) page("A3").get("period");
        assertThat(status("A3/completeQualityCheck", Map.of("period", period))).isEqualTo(400); // 异地就医 still late
        assertThat(status("A3/generateMonthlyReport", Map.of("period", period))).isEqualTo(400);
        assertThat(status("A3/retryPull", Map.of("source", "不存在的源"))).isEqualTo(400);

        Map<String, Object> first = result(post("A3/retryPull", Map.of("source", "异地就医")));
        assertThat(first).containsEntry("arrived", false).containsEntry("status", 504);
        assertThat((String) first.get("trace")).matches("[0-9a-f]{4}-[0-9a-f]{4}");
        assertThat(result(post("A3/retryPull", Map.of("source", "异地就医")))).containsEntry("arrived", true);
        assertThat(status("A3/retryPull", Map.of("source", "异地就医"))).isEqualTo(400);
        assertThat(find(page("A3").get("sources"), "name", "异地就医")).containsEntry("status", "ok").containsEntry("arrivedLate", true);

        assertThat(status("A3/completeQualityCheck", Map.of("period", "2020年1月期"))).isEqualTo(400);
        result(post("A3/completeQualityCheck", Map.of("period", period)));
        assertThat(status("A3/completeQualityCheck", Map.of("period", period))).isEqualTo(400);
        assertThat(page("A3")).containsEntry("qcDone", true);

        Map<String, Object> gen = result(post("A3/generateMonthlyReport", Map.of("period", period)));
        assertThat(gen).containsEntry("created", true).containsEntry("step", 3);
        String task = (String) gen.get("taskId");
        assertThat(jdbc.sql("select step from publish_task where id = :id").param("id", task).query(Integer.class).single()).isEqualTo(3);
        assertThat(result(post("A3/generateMonthlyReport", Map.of("period", period)))).containsEntry("created", false);
        assertThat(page("A3")).containsEntry("reportTaskId", task);

        post("A3/resetDemo", Map.of());
        assertThat(jdbc.sql("select count(*) from publish_task where id = :id").param("id", task).query(Integer.class).single()).isZero();
    }

    private Map<String, Object> indicator(String name) {
        Map<String, Object> m = new java.util.HashMap<>(Map.of(
                "name", name, "group", "效", "domain", "住院效率", "freq", "月",
                "numerator", "Σ术前住院天数", "denominator", "手术出院人次",
                "filters", List.of("手术标志 = 1"), "dims", List.of("机构", "病组"),
                "tier", "pct", "internalOnly", false));
        m.put("chart", "分位条");
        m.put("granularity", "病组");
        return m;
    }

    @Test
    void a4WizardValidatesAndListsTheIndicator() {
        Map<String, Object> bad = indicator("");
        assertThat(status("A4/submitIndicator", bad)).isEqualTo(400);
        bad = indicator("=HYPERLINK(\"x\")");
        assertThat(status("A4/submitIndicator", bad)).isEqualTo(400);
        bad = indicator("超".repeat(31));
        assertThat(status("A4/submitIndicator", bad)).isEqualTo(400);
        bad = indicator("次均总费用");
        assertThat(status("A4/submitIndicator", bad)).isEqualTo(400); // duplicate of a seeded indicator
        bad = indicator("内部指标测试");
        bad.put("internalOnly", true);
        bad.put("tier", "named");
        assertThat(status("A4/submitIndicator", bad)).isEqualTo(400); // 仅内部 cannot carry a tier
        bad = indicator("维度测试");
        bad.put("dims", List.of());
        assertThat(status("A4/submitIndicator", bad)).isEqualTo(400);
        assertThat(status("A4/submitIndicator", Map.of("qa", "probe"))).isEqualTo(400);

        String no1 = (String) result(post("A4/submitIndicator", indicator("术前平均住院日"))).get("approvalNo");
        assertThat(no1).endsWith("-0917");
        assertThat(status("A4/submitIndicator", indicator("术前平均住院日"))).isEqualTo(400);
        Map<String, Object> internal = indicator("术后并发症明细");
        internal.put("internalOnly", true);
        internal.remove("tier");
        String no2 = (String) result(post("A4/submitIndicator", internal)).get("approvalNo");
        assertThat(no2).endsWith("-0918");

        Map<String, Object> a4 = page("A4");
        assertThat(find(a4.get("indicators"), "name", "术前平均住院日"))
                .containsEntry("status", "review").containsEntry("approvalNo", no1).containsEntry("source", "local");
        assertThat(find(a4.get("indicators"), "name", "术后并发症明细")).containsEntry("source", "internal").containsEntry("tier", "none");

        // 档位变更: one open request per indicator; 仅内部 refused
        assertThat(status("A4/requestTierChange", Map.of("indicator", "单病例费用明细", "to", "named"))).isEqualTo(400);
        result(post("A4/requestTierChange", Map.of("indicator", "次均总费用", "from", "pct", "to", "anon")));
        assertThat(status("A4/requestTierChange", Map.of("indicator", "次均总费用", "from", "pct", "to", "named"))).isEqualTo(400);
        assertThat(find(page("A4").get("indicators"), "name", "次均总费用")).containsEntry("pendingTier", "anon");

        // 加入发布包: internal refused; a second add of the same items refused
        assertThat(status("A4/addToPackage", Map.of("indicators", List.of("单病例费用明细")))).isEqualTo(400);
        result(post("A4/addToPackage", Map.of("indicators", List.of("CMI值", "次均总费用"))));
        assertThat(status("A4/addToPackage", Map.of("indicators", List.of("CMI值")))).isEqualTo(400);
        assertThat(find(page("A4").get("indicators"), "name", "CMI值")).containsEntry("inPackage", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void a5ChartsAndVersionsPersist() {
        int v0 = ((Number) ((List<Map<String, Object>>) page("A5").get("templates")).get(0).get("version")).intValue();
        assertThat(status("A5/saveTemplateVersion", Map.of("template", 0))).isEqualTo(400); // nothing to save
        assertThat(status("A5/addChart", Map.of("template", 0, "section", 0, "chart", "KPI 卡"))).isEqualTo(400); // already there
        assertThat(status("A5/addChart", Map.of("template", 9, "section", 0, "chart", "趋势线"))).isEqualTo(400);
        assertThat(status("A5/addChart", Map.of("template", 0, "section", 0, "chart", "不存在"))).isEqualTo(400);
        result(post("A5/addChart", Map.of("template", 0, "section", 0, "chart", "趋势线")));
        assertThat(result(post("A5/saveTemplateVersion", Map.of("template", 0)))).containsEntry("version", v0 + 1);
        assertThat(status("A5/saveTemplateVersion", Map.of("template", 0))).isEqualTo(400);
        result(post("A5/removeChart", Map.of("template", 0, "section", 0, "chart", "趋势线")));
        assertThat(result(post("A5/saveTemplateVersion", Map.of("template", 0)))).containsEntry("version", v0 + 2);

        Map<String, Object> t0 = ((List<Map<String, Object>>) page("A5").get("templates")).get(0);
        assertThat(((Number) t0.get("version")).intValue()).isEqualTo(v0 + 2);
        assertThat((List<String>) ((List<Map<String, Object>>) t0.get("sections")).get(0).get("charts")).doesNotContain("趋势线");
    }

    @Test
    @SuppressWarnings("unchecked")
    void a6AdoptOpensA7TopicAndReviewIsGuarded() {
        Map<String, Object> gg19 = Map.of("id", "T-GG19", "title", "某肛肠专科医院 GG19 费用异常");
        // 推荐仅供参考,由行政管理组采纳: the analyst identity is refused
        assertThat(post("zhangy", "A6/adoptTopic", gg19).getStatusCode().value()).isEqualTo(403);
        assertThat(status("A6/adoptTopic", Map.of("qa", "probe"))).isEqualTo(400);
        assertThat(status("A7/approveSection", Map.of("topic", "T-GG19", "section", 0))).isEqualTo(400); // not adopted yet

        assertThat(result(post("A6/adoptTopic", gg19))).containsEntry("taskId", "tp-gg19");
        assertThat(status("A6/adoptTopic", gg19)).isEqualTo(400);
        assertThat(jdbc.sql("select step from publish_task where id = 'tp-gg19'").query(Integer.class).single()).isEqualTo(3);
        assertThat((Map<String, Object>) page("A6").get("topicStates")).containsEntry("T-GG19", "adopted");
        Map<String, Object> a7 = page("A7");
        assertThat((Map<String, Object>) ((Map<String, Object>) a7.get("topics")).get("T-GG19")).containsEntry("code", "GG19");

        // 7/7 before submit; submit once; locked afterwards; A8 task → 专家组审核
        for (int s = 0; s < 6; s++) result(post("A7/approveSection", Map.of("topic", "T-GG19", "section", s)));
        assertThat(status("A7/approveSection", Map.of("topic", "T-GG19", "section", 0))).isEqualTo(400);
        assertThat(status("A7/submitReview", Map.of("topic", "T-GG19"))).isEqualTo(400);
        result(post("A7/approveSection", Map.of("topic", "T-GG19", "section", 6)));
        assertThat(result(post("A7/submitReview", Map.of("topic", "T-GG19")))).containsEntry("step", 4);
        assertThat(status("A7/submitReview", Map.of("topic", "T-GG19"))).isEqualTo(400);
        assertThat(status("A7/regenerateSection", Map.of("topic", "T-GG19", "section", 1))).isEqualTo(400);
        assertThat((Map<String, Object>) page("A7").get("taskSteps")).containsEntry("T-GG19", 4);

        // BR25: 重新生成 an approved section puts it back to 待审定
        result(post("A7/approveSection", Map.of("section", 3)));
        Map<String, Object> regen = result(post("A7/regenerateSection", Map.of("section", 3)));
        assertThat(regen).containsEntry("reset", true);
        assertThat((List<Integer>) page("A7").get("approved")).doesNotContain(3);

        // 推送: only once per comment; creates A10 items
        int before = jdbc.sql("select count(*) from feedback_item").query(Integer.class).single();
        result(post("A7/resolveComment", Map.of("comment", 0)));
        Map<String, Object> push = result(post("A7/pushComments", Map.of("docNo", "YJD-2026-1004-01")));
        int n = ((Number) push.get("count")).intValue();
        assertThat(n).isPositive();
        assertThat(jdbc.sql("select count(*) from feedback_item").query(Integer.class).single()).isEqualTo(before + n);
        assertThat(status("A7/pushComments", Map.of("docNo", "YJD-2026-1004-01"))).isEqualTo(400);

        // 意见单导出: analyst refused (具名明细), others get a trace number
        assertThat(post("zhangy", "A7/exportCommentsExcel", Map.of("docNo", "YJD-2026-1004-01")).getStatusCode().value()).isEqualTo(403);
        assertThat((String) result(post("A7/exportCommentsExcel", Map.of("docNo", "YJD-2026-1004-01"))).get("traceNo")).startsWith("YJD-2026-1004-01-E");

        post("A7/resetDemo", Map.of());
        assertThat(jdbc.sql("select count(*) from publish_task where id = 'tp-gg19'").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from feedback_item").query(Integer.class).single()).isEqualTo(before);
    }
}
