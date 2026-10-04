package cn.ybdata.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
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
 * API-level mirror of the e2e 串联流程 (web/e2e) that assert backend overlays:
 * F5 预警 (A11 提醒函 → D1 回执 → A11 overlay) and F1's approval gate (A8 → overlay step).
 *   YB_IT_DB_URL=jdbc:postgresql://localhost:5432/ybdata_test mvn verify -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false
 * Alert rows are put back into their seed state first, so the class tolerates a reused database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "YB_IT_DB_URL", matches = ".+")
class FlowsIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("YB_IT_DB_URL"));
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcClient jdbc;

    private ResponseEntity<Map> post(String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-YB-User", "flows-it");
        return http.exchange("/api/v1/actions/" + path, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> page(String code) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-YB-User", "flows-it");
        ResponseEntity<Map> r = http.exchange("/api/v1/pages/" + code, HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("GET /pages/" + code).isTrue();
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private String alertStatus(String id) {
        List<Map<String, Object>> alerts = (List<Map<String, Object>>) page("A11").get("alerts");
        return alerts.stream().filter(a -> id.equals(a.get("id"))).map(a -> (String) a.get("status")).findFirst().orElse(null);
    }

    @Test
    void f5ReminderThenReceiptShowsInAlertOverlay() {
        jdbc.sql("update alert set status = 'unsent' where id = 'AL-01'").update();
        jdbc.sql("update alert set status = 'sent' where id = 'AL-06'").update();

        // A11 发送提醒函: unsent → sent, and only once
        assertThat(post("A11/sendReminder", Map.of("alertId", "AL-01", "org", "某肛肠专科医院")).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(alertStatus("AL-01")).isEqualTo("sent");
        assertThat(post("A11/sendReminder", Map.of("alertId", "AL-01", "org", "某肛肠专科医院")).getStatusCode().value()).isEqualTo(400);

        // D1 回执 for the IU29 提醒函 → AL-06 becomes ack in the A11 read model
        ResponseEntity<Map> rc = post("D1/submitReceipt",
                Map.of("alert", "IU29 例均基金差额超阈值", "category", "编码调整", "text", "已完成编码培训"));
        assertThat(rc.getStatusCode().is2xxSuccessful()).as("submitReceipt → " + rc.getBody()).isTrue();
        assertThat(alertStatus("AL-06")).isEqualTo("ack");
        assertThat(jdbc.sql("select count(*) from alert_receipt where alert_id = 'AL-06'").query(Long.class).single()).isPositive();
    }

    @Test
    void f5ReceiptForUnknownAlertIsRefused() {
        assertThat(post("D1/submitReceipt", Map.of("alert", "ZZ99 不存在", "category", "其他", "text", "x"))
                .getStatusCode().value()).isEqualTo(400);
    }

    @Test
    @SuppressWarnings("unchecked")
    void f1ApprovalMovesTaskPastTheGateInA8Overlay() {
        post("A8/resetDemo", Map.of("taskId", "m8"));
        try {
            Map<String, Object> before = ((List<Map<String, Object>>) page("A8").get("tasks")).stream()
                    .filter(t -> "m8".equals(t.get("id"))).findFirst().orElseThrow();
            assertThat(((Number) before.get("step")).intValue()).isEqualTo(5);

            assertThat(post("A8/approvePublish", Map.of("taskId", "m8", "comment", "同意", "coverage", 18))
                    .getStatusCode().is2xxSuccessful()).isTrue();
            Map<String, Object> after = ((List<Map<String, Object>>) page("A8").get("tasks")).stream()
                    .filter(t -> "m8".equals(t.get("id"))).findFirst().orElseThrow();
            assertThat(((Number) after.get("step")).intValue()).isEqualTo(6);
        } finally {
            post("A8/resetDemo", Map.of("taskId", "m8"));
        }
    }
}
