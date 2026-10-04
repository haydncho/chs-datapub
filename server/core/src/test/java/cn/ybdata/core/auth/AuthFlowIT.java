package cn.ybdata.core.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Login → session → access matrix → data scope, against a real PostgreSQL database:
 *   YB_IT_DB_URL=jdbc:postgresql://localhost:5432/ybdata_auth_it mvn verify -Dtest='AuthFlowIT'
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "YB_IT_DB_URL", matches = ".+")
class AuthFlowIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("YB_IT_DB_URL"));
        r.add("yb.auth.secret", () -> "integration-test-secret-0123456789abcdef");
    }

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    @Autowired TestRestTemplate http;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void reset() {
        jdbc.sql("delete from auth_sms_code").update();
        jdbc.sql("delete from auth_attempt").update();
    }

    private ResponseEntity<Map<String, Object>> call(HttpMethod m, String path, String token, Object body) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        return http.exchange("/api/v1" + path, m, new HttpEntity<>(body, h), MAP);
    }

    private ResponseEntity<String> getText(String path, String token) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        return http.exchange("/api/v1" + path, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private Map<String, Object> login(String method, String account, String secret) {
        var r = call(HttpMethod.POST, "/auth/login", null,
                Map.of("method", method, "account", account, "cert".equals(method) ? "pin" : "code", secret));
        assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);
        return r.getBody();
    }

    @Test
    @SuppressWarnings("unchecked")
    void smsLoginAsHospitalIsScopedAndAudited() {
        var sent = call(HttpMethod.POST, "/auth/sms-code", null, Map.of("account", "limin"));
        assertThat(sent.getStatusCode().value()).isEqualTo(200);
        assertThat(sent.getBody()).containsEntry("cooldown", 60).containsEntry("phone", "138****0005");
        var again = call(HttpMethod.POST, "/auth/sms-code", null, Map.of("account", "limin"));
        assertThat(again.getStatusCode().value()).isEqualTo(429);
        assertThat(again.getHeaders().getFirst("Retry-After")).isNotNull();

        assertThat(call(HttpMethod.POST, "/auth/login", null, Map.of("method", "sms", "account", "limin", "code", "000000"))
                .getStatusCode().value()).isEqualTo(401);
        Map<String, Object> s = login("sms", "limin", "123456");
        String token = (String) s.get("token");
        assertThat((Map<String, Object>) s.get("identity")).containsEntry("role", "hospital").containsEntry("target", "B1");
        assertThat((List<String>) s.get("pages")).contains("B1", "D1", "cockpit").doesNotContain("A3");
        // a used code cannot be replayed
        assertThat(call(HttpMethod.POST, "/auth/login", null, Map.of("method", "sms", "account", "limin", "code", "123456"))
                .getStatusCode().value()).isEqualTo(401);

        assertThat(getText("/pages/B4", token).getStatusCode().value()).isEqualTo(200);
        var forbidden = getText("/pages/A10", token);
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(forbidden.getBody()).contains("error");
        assertThat(call(HttpMethod.POST, "/actions/A8/approvePublish", token, Map.of("taskId", "m8")).getStatusCode().value()).isEqualTo(403);
        assertThat(getText("/audit", token).getStatusCode().value()).isEqualTo(403);

        String cockpit = getText("/pages/cockpit", token).getBody();
        assertThat(cockpit).contains("\"id\":\"hosp\"").doesNotContain("\"id\":\"conv\"")
                .doesNotContain("某肛肠专科医院").doesNotContain("第二人民医院").contains("\"institutions\":[]");

        var act = call(HttpMethod.POST, "/actions/B4/exportReport", token, Map.of("name", "x"));
        assertThat(act.getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.sql("select actor from audit_event where id = :id").param("id", ((Number) act.getBody().get("auditId")).longValue())
                .query(String.class).single()).isEqualTo("李敏");

        assertThat(call(HttpMethod.POST, "/auth/logout", token, null).getStatusCode().value()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/auth/me", token, null).getStatusCode().value()).isEqualTo(401);
        assertThat(getText("/pages/B4", token).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @SuppressWarnings("unchecked")
    void certLoginAndIdentitySwitch() {
        assertThat(call(HttpMethod.POST, "/auth/login", null, Map.of("method", "cert", "account", "chenzy", "pin", "000000"))
                .getStatusCode().value()).isEqualTo(401);
        Map<String, Object> s = login("cert", "chenzy", "123456");
        String token = (String) s.get("token");
        List<Map<String, Object>> ids = (List<Map<String, Object>>) s.get("identities");
        assertThat(ids).extracting(m -> m.get("role")).containsExactly("convener", "admin", "hospital");
        assertThat((Map<String, Object>) s.get("viewer")).containsEntry("name", "陈志远").containsEntry("role", "召集人");
        assertThat(getText("/pages/cockpit", token).getBody()).contains("\"id\":\"conv\"");
        assertThat(getText("/pages/A10", token).getStatusCode().value()).isEqualTo(200);

        Object hospId = ids.get(2).get("id");
        var sw = call(HttpMethod.POST, "/auth/identity", token, Map.of("identity", hospId));
        assertThat(sw.getStatusCode().value()).isEqualTo(200);
        String hospToken = (String) sw.getBody().get("token");
        assertThat(hospToken).isNotEqualTo(token);
        assertThat(call(HttpMethod.GET, "/auth/me", token, null).getStatusCode().value()).isEqualTo(401); // old token revoked
        assertThat(getText("/pages/cockpit", hospToken).getBody()).doesNotContain("\"id\":\"conv\"").contains("陈志远");
        assertThat(getText("/pages/A10", hospToken).getStatusCode().value()).isEqualTo(403);

        // an identity the user does not hold
        var foreign = jdbc.sql("select i.id from user_identity i join app_user u on u.id = i.user_id where u.login = 'zhoumin'")
                .query(Long.class).single();
        assertThat(call(HttpMethod.POST, "/auth/identity", hospToken, Map.of("identity", foreign)).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void auditorIsReadOnlyAndObserverOnlySeesC3() {
        String auditor = (String) login("cert", "zhaoan", "123456").get("token");
        assertThat(getText("/pages/A14", auditor).getStatusCode().value()).isEqualTo(200);
        assertThat(getText("/audit/verify", auditor).getStatusCode().value()).isEqualTo(200);
        assertThat(call(HttpMethod.POST, "/actions/A14/exportLog", auditor, Map.of()).getStatusCode().value()).isEqualTo(403);
        assertThat(getText("/pages/A8", auditor).getStatusCode().value()).isEqualTo(403);

        String observer = (String) login("cert", "zhoumin", "123456").get("token");
        assertThat(getText("/pages/C3", observer).getStatusCode().value()).isEqualTo(200);
        assertThat(getText("/pages/cockpit", observer).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void lockOutAfterRepeatedFailures() {
        for (int i = 0; i < 5; i++) {
            assertThat(call(HttpMethod.POST, "/auth/login", null, Map.of("method", "cert", "account", "wangq", "pin", "999999"))
                    .getStatusCode().value()).isEqualTo(401);
        }
        var locked = call(HttpMethod.POST, "/auth/login", null, Map.of("method", "cert", "account", "wangq", "pin", "123456"));
        assertThat(locked.getStatusCode().value()).isEqualTo(429);
        assertThat(jdbc.sql("select count(*) from audit_event where action = 'loginFailed' and actor = 'wangq'").query(Long.class).single())
                .isGreaterThanOrEqualTo(5);
    }

    @Test
    void tokensAndDevFallback() {
        assertThat(getText("/pages/B4", "yb1.forged.token").getStatusCode().value()).isEqualTo(401);
        assertThat(call(HttpMethod.GET, "/auth/me", null, null).getStatusCode().value()).isEqualTo(401);
        // dev mode (default): no credentials → legacy demo identity, unrestricted
        assertThat(getText("/pages/A10", null).getStatusCode().value()).isEqualTo(200);
        assertThat(getText("/pages/A1", null).getStatusCode().value()).isEqualTo(200);
        // dev mode: X-YB-User naming a known user is access-checked as that user
        HttpHeaders h = new HttpHeaders();
        h.set("X-YB-User", "limin");
        assertThat(http.exchange("/api/v1/pages/A10", HttpMethod.GET, new HttpEntity<>(h), String.class).getStatusCode().value()).isEqualTo(403);
        assertThat(http.exchange("/api/v1/pages/B3", HttpMethod.GET, new HttpEntity<>(h), String.class).getStatusCode().value()).isEqualTo(200);
        // missing fields
        assertThat(call(HttpMethod.POST, "/auth/login", null, Map.of("method", "cert", "account", "chenzy")).getStatusCode().value()).isEqualTo(400);
        assertThat(call(HttpMethod.POST, "/auth/login", null, Map.of("method", "x", "account", "chenzy", "pin", "1")).getStatusCode().value()).isEqualTo(400);
    }
}
