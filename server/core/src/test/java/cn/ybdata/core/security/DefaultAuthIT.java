package cn.ybdata.core.security;

import static org.assertj.core.api.Assertions.assertThat;

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
 * The shipped configuration (no {@code yb.auth.dev-header} override): nothing but a bearer token opens the API.
 *   YB_IT_DB_URL=jdbc:postgresql://localhost:5432/ybdata_test mvn verify -Dtest='DefaultAuthIT'
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "YB_IT_DB_URL", matches = ".+")
class DefaultAuthIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("YB_IT_DB_URL"));
    }

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    @Autowired TestRestTemplate http;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void reset() {
        jdbc.sql("delete from auth_sms_code").update();
        jdbc.sql("delete from auth_attempt").update();
    }

    private ResponseEntity<Map<String, Object>> call(HttpMethod m, String path, HttpHeaders h, Object body) {
        return http.exchange("/api/v1" + path, m, new HttpEntity<>(body, h == null ? new HttpHeaders() : h), MAP);
    }

    @Test
    void withoutABearerTokenEverythingButLoginIs401() {
        HttpHeaders dev = new HttpHeaders();
        dev.set("X-YB-User", "chenzy");
        for (HttpHeaders h : new HttpHeaders[] {null, dev}) {
            assertThat(call(HttpMethod.GET, "/pages/A12", h, null).getStatusCode().value()).isEqualTo(401);
            assertThat(call(HttpMethod.GET, "/pages/B1", h, null).getStatusCode().value()).isEqualTo(401);
            assertThat(call(HttpMethod.GET, "/audit", h, null).getStatusCode().value()).isEqualTo(401);
            assertThat(call(HttpMethod.GET, "/settings/appearance", h, null).getStatusCode().value()).isEqualTo(401);
            assertThat(call(HttpMethod.GET, "/analytics/health", h, null).getStatusCode().value()).isEqualTo(401);
            var act = call(HttpMethod.POST, "/actions/A12/requestAddUser", h, Map.of("name", "x", "login", "qa_noauth9", "role", "convener"));
            assertThat(act.getStatusCode().value()).isEqualTo(401);
            assertThat(act.getBody()).containsKey("error");
        }
        assertThat(jdbc.sql("select count(*) from user_request where login = 'qa_noauth9'").query(Long.class).single()).isZero();
        // the public login page payload names no account
        ResponseEntity<String> a1 = http.getForEntity("/api/v1/pages/A1", String.class);
        assertThat(a1.getStatusCode().value()).isEqualTo(200);
        assertThat(a1.getBody()).doesNotContain("\"account\"");
    }

    @Test
    void smsCodeAnswerDoesNotRevealAccounts() {
        var known = call(HttpMethod.POST, "/auth/sms-code", null, Map.of("account", "zhaoan"));
        var unknown = call(HttpMethod.POST, "/auth/sms-code", null, Map.of("account", "nobody_qa_x"));
        assertThat(known.getStatusCode().value()).isEqualTo(200);
        assertThat(unknown.getStatusCode().value()).isEqualTo(200);
        assertThat(known.getBody()).isEqualTo(unknown.getBody());
        // login name and phone number share one cool-down (zhaoan = 13800000006)
        assertThat(call(HttpMethod.POST, "/auth/sms-code", null, Map.of("account", "13800000006")).getStatusCode().value()).isEqualTo(429);
        // NUL / control characters are a 400, not a database error
        assertThat(call(HttpMethod.POST, "/auth/sms-code", null, Map.of("account", "a\u0000b")).getStatusCode().value()).isEqualTo(400);
        assertThat(call(HttpMethod.POST, "/auth/login", null, Map.of("method", "cert", "account", "a\u0000b", "pin", "1")).getStatusCode().value())
                .isEqualTo(400);
    }

    @Test
    void lockOutCountsLoginNameAndPhoneTogether() {
        // wangq = 13800000003: 3 failures by name + 2 by phone lock the user
        for (int i = 0; i < 3; i++) {
            assertThat(call(HttpMethod.POST, "/auth/login", null, Map.of("method", "cert", "account", "wangq", "pin", "999999"))
                    .getStatusCode().value()).isEqualTo(401);
        }
        for (int i = 0; i < 2; i++) {
            assertThat(call(HttpMethod.POST, "/auth/login", null, Map.of("method", "cert", "account", "13800000003", "pin", "999999"))
                    .getStatusCode().value()).isEqualTo(401);
        }
        assertThat(call(HttpMethod.POST, "/auth/login", null, Map.of("method", "cert", "account", "wangq", "pin", "123456"))
                .getStatusCode().value()).isEqualTo(429);
        jdbc.sql("delete from auth_attempt").update();
    }
}
