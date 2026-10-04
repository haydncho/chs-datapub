package cn.ybdata.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * End-to-end against a real (empty) PostgreSQL database:
 *   YB_IT_DB_URL=jdbc:postgresql://localhost:5432/ybdata_test mvn verify
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "YB_IT_DB_URL", matches = ".+")
class PlatformIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("YB_IT_DB_URL"));
        r.add("spring.flyway.clean-disabled", () -> "false");
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcClient jdbc;

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body) {
        return http.postForObject("/api/v1/actions/" + path, body, Map.class);
    }

    @Test
    void mobileSignOffShowsUpInWebReportCenterAndIsAudited() {
        post("D1/signReport", Map.of("report", "2026年8月 本院医保运行报告", "version", "v1"));
        String b4 = http.getForObject("/api/v1/pages/B4", String.class);
        assertThat(b4).contains("\"name\":\"2026年8月 DRG月度运行报告\"");
        assertThat(jdbc.sql("select status from report where id = 'R-2026-08'").query(String.class).single()).isEqualTo("signed");
        assertThat(http.getForObject("/api/v1/audit/verify", Map.class)).containsEntry("valid", true);
    }

    @Test
    void approvalGateAndRejectRules() {
        // reject without a comment is refused
        assertThat(http.postForEntity("/api/v1/actions/A8/rejectPublish",
                Map.of("taskId", "m8", "toStep", 3, "comment", " "), Map.class).getStatusCode().value()).isEqualTo(400);
        // approve moves the task to 定向发布
        Map<String, Object> r = post("A8/approvePublish", Map.of("taskId", "m8", "comment", "同意"));
        assertThat((Map<String, Object>) r.get("result")).containsEntry("step", 6);
        // a second approval is refused (no longer at the approval node)
        assertThat(http.postForEntity("/api/v1/actions/A8/approvePublish",
                Map.of("taskId", "m8"), Map.class).getStatusCode().value()).isEqualTo(400);
        post("A8/resetDemo", Map.of("taskId", "m8"));
    }

    @Test
    void verificationDifferencesBecomeFeedbackItems() {
        Map<String, Object> r = post("B5/submitVerification", Map.of("items", java.util.List.of(
                Map.of("id", "cases", "result", "diff", "ownValue", "280", "reason", "特例单议"),
                Map.of("id", "fund", "result", "ok"))));
        String a10 = http.getForObject("/api/v1/pages/A10", String.class);
        String created = ((java.util.List<String>) ((Map<String, Object>) r.get("result")).get("created")).get(0);
        assertThat(a10).contains(created).contains("核对差异 · cases");
    }
}
