package cn.ybdata.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * A12 用户与权限 against a real PostgreSQL database:
 *   YB_IT_DB_URL=jdbc:postgresql://localhost:5432/ybdata_users_it mvn verify -Dtest=UserApiIT
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "YB_IT_DB_URL", matches = ".+")
class UserApiIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("YB_IT_DB_URL"));
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcClient jdbc;

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> call(HttpMethod m, String path, String as, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-YB-User", as);
        return (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) http.exchange("/api/v1/" + path, m, new HttpEntity<>(body, h), Map.class);
    }

    private ResponseEntity<Map<String, Object>> act(String as, String action, Map<String, Object> body) {
        return call(HttpMethod.POST, "actions/A12/" + action, as, body);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> users() {
        return (List<Map<String, Object>>) call(HttpMethod.GET, "pages/A12", "chenzy", null).getBody().get("users");
    }

    private Map<String, Object> user(String login) {
        return users().stream().filter(u -> login.equals(u.get("login"))).findFirst().orElseThrow();
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageShowsRealUsersRolesAndMatrix() {
        Map<String, Object> p = call(HttpMethod.GET, "pages/A12", "chenzy", null).getBody();
        List<Map<String, Object>> roles = (List<Map<String, Object>>) p.get("roles");
        assertThat(roles).extracting(r -> r.get("name")).containsExactly(
                "召集人", "行政管理组", "委托分析团队", "医保办主任", "县区医保部门", "安全审计员", "社会监督员");
        Map<String, Object> conv = roles.get(0);
        assertThat((List<String>) conv.get("pages")).hasSize(24).contains("A12", "cockpit");
        assertThat(conv.get("canApprove")).isEqualTo(true);
        assertThat(roles.get(5).get("readOnly")).isEqualTo(true);

        List<Map<String, Object>> users = (List<Map<String, Object>>) p.get("users");
        assertThat(users.stream().map(u -> u.get("login")).toList()).contains(
                "chenzy", "lihua", "wangq", "zhangy", "limin", "zhaoan", "zhoumin");
        Map<String, Object> limin = user("limin");
        assertThat(limin.get("role")).isEqualTo("医保办主任");
        assertThat(limin.get("org")).isEqualTo("示例市第一人民医院");
        assertThat(limin.get("side")).isEqualTo("org");
        assertThat(user("chenzy").get("side")).isEqualTo("bureau");
        assertThat((List<String>) user("chenzy").get("sides")).containsExactly("bureau", "org");
        assertThat(((List<?>) user("chenzy").get("identities"))).hasSize(3);
        assertThat(user("wangq").get("title")).isEqualTo("承办人");
        // zhoumin 种子为 40 天未登录,但其它 IT 可能已让其登录;状态判定由 UserMatrixTest 覆盖
        assertThat(user("zhoumin").get("status")).isIn("on", "expiring");

        List<Map<String, Object>> matrix = (List<Map<String, Object>>) p.get("matrix");
        assertThat(matrix).hasSize(7);
        List<Map<String, Object>> auditorCells = (List<Map<String, Object>>) matrix.get(5).get("cells");
        assertThat(auditorCells).hasSize(8);
        assertThat(auditorCells.stream().filter(c -> "gov".equals(c.get("group"))).findFirst().orElseThrow().get("level")).isEqualTo("readonly");
        Map<String, Object> summary = (Map<String, Object>) p.get("summary");
        assertThat(((Number) summary.get("total")).intValue()).isGreaterThanOrEqualTo(7);
        assertThat(((Number) summary.get("expiring")).intValue()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void onlyConvenerMayDisableAndEnable() {
        assertThat(act("lihua", "setUserEnabled", Map.of("login", "zhaoan", "enabled", false)).getStatusCode().value()).isEqualTo(403);
        assertThat(user("zhaoan").get("status")).isNotEqualTo("off");

        assertThat(act("chenzy", "setUserEnabled", Map.of("login", "zhaoan", "enabled", false)).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(user("zhaoan").get("status")).isEqualTo("off");
        assertThat(jdbc.sql("select count(*) from audit_event where page = 'A12' and action = 'setUserEnabled'").query(Integer.class).single())
                .isGreaterThanOrEqualTo(1);
        assertThat(act("chenzy", "setUserEnabled", Map.of("login", "zhaoan", "enabled", true)).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(user("zhaoan").get("status")).isEqualTo("on");

        assertThat(act("chenzy", "setUserEnabled", Map.of("login", "chenzy", "enabled", false)).getStatusCode().is4xxClientError()).isTrue();
        assertThat(act("chenzy", "setUserEnabled", Map.of("login", "nobody_x", "enabled", false)).getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void addUserNeedsSecondReviewer() {
        String login = "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Map<String, Object> req = Map.of("name", "测试用户", "login", login, "role", "analyst", "org", "示例市医保局");

        assertThat(act("lihua", "requestAddUser", req).getStatusCode().value()).isEqualTo(403);
        assertThat(act("chenzy", "requestAddUser", Map.of("name", "x", "login", "Bad Login", "role", "analyst")).getStatusCode().is4xxClientError()).isTrue();
        assertThat(act("chenzy", "requestAddUser", req).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(act("chenzy", "requestAddUser", req).getStatusCode().is4xxClientError()).as("重复申请").isTrue();

        Map<String, Object> pending = user(login);
        assertThat(pending.get("status")).isEqualTo("pending");
        assertThat(pending.get("requestedBy")).isEqualTo("陈志远");
        assertThat(jdbc.sql("select count(*) from app_user where login = :l").param("l", login).query(Integer.class).single()).isZero();

        // 申请人不能复核自己的申请;分析团队无权复核
        assertThat(act("chenzy", "reviewAddUser", Map.of("login", login, "approve", true)).getStatusCode().value()).isEqualTo(403);
        assertThat(act("zhangy", "reviewAddUser", Map.of("login", login, "approve", true)).getStatusCode().value()).isEqualTo(403);

        assertThat(act("lihua", "reviewAddUser", Map.of("login", login, "approve", true)).getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> created = user(login);
        assertThat(created.get("status")).isEqualTo("on");
        assertThat(created.get("role")).isEqualTo("委托分析团队");
        assertThat(jdbc.sql("select count(*) from user_identity i join app_user u on u.id = i.user_id where u.login = :l")
                .param("l", login).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void rejectedRequestCreatesNoUser() {
        String login = "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        assertThat(act("chenzy", "requestAddUser", Map.of("name", "被拒用户", "login", login, "role", "observer")).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(act("lihua", "reviewAddUser", Map.of("login", login, "approve", false)).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(users().stream().anyMatch(u -> login.equals(u.get("login")))).isFalse();
        assertThat(act("lihua", "reviewAddUser", Map.of("login", login, "approve", true)).getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void auditorAndOthersCannotOpenOrAct() {
        assertThat(call(HttpMethod.GET, "pages/A12", "zhaoan", null).getStatusCode().value()).isEqualTo(403);
        assertThat(act("zhangy", "setUserEnabled", Map.of("login", "zhaoan", "enabled", false)).getStatusCode().value()).isEqualTo(403);
    }
}
