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
        DevHeader.enable(r);
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcClient jdbc;

    @org.junit.jupiter.api.BeforeEach
    void asConvener() {
        DevHeader.defaultUser(http, "chenzy");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body) {
        return http.postForObject("/api/v1/actions/" + path, body, Map.class);
    }

    private org.springframework.http.ResponseEntity<Map> as(String user, String path, Object body) {
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.set("X-YB-User", user);
        return http.exchange("/api/v1/actions/" + path, org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(body, h), Map.class);
    }

    @Test
    void mobileSignOffShowsUpInWebReportCenterAndIsAudited() {
        as("chenzy", "A8/resetDemo", Map.of("taskId", "m8"));
        try {
            assertThat(as("chenzy", "A8/approvePublish", Map.of("taskId", "m8", "institutions", java.util.List.of("第一人民医院")))
                    .getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(as("limin", "D1/signReport", Map.of("reportId", "R-2026-08", "version", "v1"))
                    .getStatusCode().is2xxSuccessful()).isTrue();
            org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
            h.set("X-YB-User", "limin");
            String b4 = http.exchange("/api/v1/pages/B4", org.springframework.http.HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(h), String.class).getBody();
            assertThat(b4).contains("\"name\":\"2026年8月 DRG月度运行报告\"");
            assertThat(jdbc.sql("select count(*) from report_signoff where report_id = 'R-2026-08' and org_id = 'H001' and channel = 'mobile'")
                    .query(Integer.class).single()).isEqualTo(1);
            assertThat(http.getForObject("/api/v1/audit/verify", Map.class)).containsEntry("valid", true);
        } finally {
            as("chenzy", "A8/resetDemo", Map.of("taskId", "m8"));
        }
    }

    @Test
    void approvalGateAndRejectRules() {
        as("chenzy", "A8/resetDemo", Map.of("taskId", "m8"));
        // reject without a comment is refused
        assertThat(as("chenzy", "A8/rejectPublish",
                Map.of("taskId", "m8", "toStep", 3, "comment", " ")).getStatusCode().value()).isEqualTo(400);
        // approve moves the task to 定向发布
        Map<String, Object> r = as("chenzy", "A8/approvePublish",
                Map.of("taskId", "m8", "comment", "同意", "institutions", java.util.List.of("第一人民医院"))).getBody();
        assertThat((Map<String, Object>) r.get("result")).containsEntry("step", 6);
        // a second approval is refused (no longer at the approval node)
        assertThat(as("chenzy", "A8/approvePublish",
                Map.of("taskId", "m8", "institutions", java.util.List.of("第一人民医院"))).getStatusCode().value()).isEqualTo(400);
        as("chenzy", "A8/resetDemo", Map.of("taskId", "m8"));
    }

    @Test
    void verificationDifferencesBecomeFeedbackItems() {
        // one submission per institution and round, before the deadline
        jdbc.sql("delete from verification_submission").update();
        jdbc.sql("update verification_round set deadline = now() + interval '3 days'").update();
        // B5 is submitted by the institution itself (limin · H001), never by a 医保局 identity
        var h = new org.springframework.http.HttpHeaders();
        h.set(DevHeader.HEADER, "limin");
        Map<String, Object> r = http.exchange("/api/v1/actions/B5/submitVerification", org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(Map.of("items", java.util.List.of(
                        Map.of("id", "cases", "result", "diff", "ownValue", "280", "reason", "特例单议"),
                        Map.of("id", "fundDiff", "result", "ok"), Map.of("id", "comorbidity", "result", "ok"),
                        Map.of("id", "los", "result", "ok"))), h), Map.class).getBody();
        String a10 = http.getForObject("/api/v1/pages/A10", String.class);
        String created = ((java.util.List<String>) ((Map<String, Object>) r.get("result")).get("created")).get(0);
        assertThat(a10).contains(created).contains("核对差异 · BR25 本院病例数");
    }
}
