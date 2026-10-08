package cn.ybdata.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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
 * 机构端数据范围 + B6 学习状态, against a real PostgreSQL database:
 *   YB_IT_DB_URL=jdbc:postgresql://localhost:5432/ybdata_test mvn verify -Dtest='HospitalScopeIT'
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "YB_IT_DB_URL", matches = ".+")
class HospitalScopeIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("YB_IT_DB_URL"));
        r.add("yb.auth.secret", () -> "integration-test-secret-0123456789abcdef");
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcClient jdbc;
    private final ObjectMapper json = new ObjectMapper();

    /** a 示例市第二人民医院 (H002) 医保办 user with limin's demo PIN */
    @BeforeEach
    void h002User() {
        jdbc.sql("delete from auth_attempt").update();
        jdbc.sql("insert into app_user (login, name, role_code, org_id) values ('it_h002', '钱二', 'hospital', 'H002') on conflict (login) do nothing").update();
        jdbc.sql("""
                insert into user_identity (user_id, role_code, org_id, label, description, zone, tone, initial, target, scope, side)
                select u.id, 'hospital', 'H002', '定点医药机构 · 示例市第二人民医院', '本院具名', '发布区', 'ok', '院', 'B1', '示例市第二人民医院 · 本院具名', 'org'
                from app_user u where u.login = 'it_h002' on conflict do nothing""").update();
        jdbc.sql("""
                insert into user_credential (user_id, kind, secret_hash)
                select u.id, 'pin', c.secret_hash from app_user u, user_credential c join app_user l on l.id = c.user_id and l.login = 'limin'
                where u.login = 'it_h002' on conflict do nothing""").update();
    }

    private String login(String account) {
        var r = http.postForEntity("/api/v1/auth/login", Map.of("method", "cert", "account", account, "pin", "123456"), Map.class);
        assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);
        return (String) r.getBody().get("token");
    }

    private ResponseEntity<String> exchange(HttpMethod m, String path, String token, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return http.exchange("/api/v1" + path, m, new HttpEntity<>(body, h), String.class);
    }

    private JsonNode page(String code, String token) throws Exception {
        var r = exchange(HttpMethod.GET, "/pages/" + code, token, null);
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        return json.readTree(r.getBody());
    }

    @Test
    void anotherHospitalNeverReceivesH001NamedData() throws Exception {
        String t = login("it_h002");
        for (String code : List.of("B1", "B2", "B4", "B5", "B7", "D1", "cockpit")) {
            JsonNode p = page(code, t);
            assertThat(p.path("noOwnData").asBoolean()).as(code).isTrue();
            String s = p.toString();
            assertThat(s).as(code).doesNotContain("示例市第一人民医院").doesNotContain("李敏")
                    .doesNotContain("4,862").doesNotContain("3,412").doesNotContain("神经内科一组");
        }
        assertThat(page("B1", t).path("hospital").path("name").asText()).isEqualTo("示例市第二人民医院");
        assertThat(page("B1", t).path("drgs")).isEmpty();
        assertThat(page("B2", t).path("groups")).isEmpty();
        assertThat(page("B4", t).path("contents")).isEmpty(); // the released list stays (per-institution sign-off), never H001 content

        // B3: H002 is in the peer group → its own position, never H001's
        JsonNode b3 = page("B3", t);
        assertThat(b3.path("peers").get(b3.path("ownIndex").asInt()).asText()).isEqualTo("示例市第二人民医院");
        for (JsonNode m : b3.path("metrics")) {
            if (!"named".equals(m.path("tier").asText())) assertThat(m.has("values")).as(m.path("name").asText()).isFalse();
        }
    }

    @Test
    void ownHospitalKeepsItsDataButAnonymousPeersAreDetached() throws Exception {
        String t = login("limin");
        assertThat(page("B1", t).path("drgs").size()).isGreaterThan(0);
        assertThat(page("B1", t).has("noOwnData")).isFalse();
        JsonNode b3 = page("B3", t);
        assertThat(b3.path("peers").get(b3.path("ownIndex").asInt()).asText()).isEqualTo("示例市第一人民医院");
        for (JsonNode m : b3.path("metrics")) {
            if ("named".equals(m.path("tier").asText())) continue;
            assertThat(m.has("values")).isFalse();
            assertThat(m.path("others").size()).isEqualTo(b3.path("peers").size() - 1);
        }
        JsonNode cockpit = page("cockpit", t);
        for (JsonNode r : cockpit.path("peers")) {
            assertThat(r.path("ownIndex").asInt()).isEqualTo(2);
            assertThat(r.path("others").size()).isEqualTo(r.path("values").size() - 1);
        }
    }

    @Test
    void trainingIsCompletedOnlyByPassingTheQuizAndPersists() throws Exception {
        jdbc.sql("delete from training_progress").update();
        String t = login("limin");
        JsonNode b6 = page("B6", t);
        for (JsonNode c : b6.path("courses")) {
            assertThat(c.path("done").asBoolean()).isFalse();
            assertThat(c.path("score").isNull()).isTrue();
            for (JsonNode q : c.path("quiz")) assertThat(q.has("answer")).isFalse();
        }
        assertThat(exchange(HttpMethod.POST, "/actions/B6/startCourse", t, Map.of("id", "C3")).getStatusCode().value()).isEqualTo(200);
        assertThat(exchange(HttpMethod.POST, "/actions/B6/startCourse", t, Map.of("id", "NOPE")).getStatusCode().value()).isEqualTo(400);
        assertThat(exchange(HttpMethod.POST, "/actions/B6/submitQuiz", t, Map.of("id", "C3", "answers", List.of(0))).getStatusCode().value()).isEqualTo(400);

        // wrong answers: graded, not completed
        JsonNode fail = json.readTree(exchange(HttpMethod.POST, "/actions/B6/submitQuiz", t, Map.of("id", "C3", "answers", List.of(2, 2))).getBody());
        assertThat(fail.path("result").path("passed").asBoolean()).isFalse();
        JsonNode c3 = course(page("B6", t), "C3");
        assertThat(c3.path("started").asBoolean()).isTrue();
        assertThat(c3.path("done").asBoolean()).isFalse();

        // right answers (C3 key: 0, 1)
        JsonNode pass = json.readTree(exchange(HttpMethod.POST, "/actions/B6/submitQuiz", t, Map.of("id", "C3", "answers", List.of(0, 1))).getBody());
        assertThat(pass.path("result").path("score").asInt()).isEqualTo(100);
        c3 = course(page("B6", t), "C3");
        assertThat(c3.path("done").asBoolean()).isTrue();
        assertThat(c3.path("score").asInt()).isEqualTo(100);

        // another user's view is unaffected
        assertThat(course(page("B6", login("it_h002")), "C3").path("done").asBoolean()).isFalse();
    }

    private static JsonNode course(JsonNode b6, String id) {
        for (JsonNode c : b6.path("courses")) if (id.equals(c.path("id").asText())) return c;
        throw new AssertionError("no course " + id);
    }
}
