package cn.ybdata.core;

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
 * 全景图订阅推送与告警确认真正落库,并在之后的会话里回显:
 *   YB_IT_DB_URL=jdbc:postgresql://localhost:5432/ybdata_test mvn verify -Dtest='CockpitIT'
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "YB_IT_DB_URL", matches = ".+")
class CockpitIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("YB_IT_DB_URL"));
        r.add("yb.auth.secret", () -> "integration-test-secret-0123456789abcdef");
    }

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};
    private static final String ALARM = "某肛肠专科医院 · GG19 次均 +23.6%";

    @Autowired TestRestTemplate http;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void reset() {
        jdbc.sql("delete from cockpit_subscription where owner in ('limin', 'lihua', 'chenzy')").update();
        jdbc.sql("delete from cockpit_alarm_ack where scope in ('bureau', 'org:H001')").update();
        jdbc.sql("delete from auth_attempt").update();
    }

    private ResponseEntity<Map<String, Object>> call(HttpMethod m, String path, String token, Object body) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        return http.exchange("/api/v1" + path, m, new HttpEntity<>(body, h), MAP);
    }

    private String login(String account, String side) {
        var r = call(HttpMethod.POST, "/auth/login", null, Map.of("method", "cert", "account", account, "pin", "123456", "side", side));
        assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);
        return (String) r.getBody().get("token");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> identity(String token, String id) {
        var r = call(HttpMethod.GET, "/pages/cockpit", token, null);
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        return ((List<Map<String, Object>>) r.getBody().get("identities")).stream()
                .filter(x -> id.equals(x.get("id"))).findFirst().orElseThrow();
    }

    private ResponseEntity<Map<String, Object>> act(String token, String action, Map<String, Object> body) {
        return call(HttpMethod.POST, "/actions/cockpit/" + action, token, body);
    }

    @Test
    @SuppressWarnings("unchecked")
    void subscriptionIsValidatedSavedAndShownInLaterSessions() {
        String limin = login("limin", "org");
        assertThat(identity(limin, "hosp").get("savedSubscription")).isNull();

        Map<String, Object> ok = Map.of("identity", "hosp", "frequency", "每月 5 日", "channel", "短信",
                "contents", List.of("告警汇总", "待办提醒"), "recipients", List.of("李敏 · 医保办", "质控科"));

        // 空内容 / 空接收人 / 越权视角 / 不在选项内的渠道都被拒绝,且不落库
        var empty = act(limin, "saveSubscription", Map.of("identity", "hosp", "frequency", "每月 5 日", "channel", "短信",
                "contents", List.of(), "recipients", List.of()));
        assertThat(empty.getStatusCode().value()).isEqualTo(400);
        assertThat(empty.getBody()).containsEntry("error", "请至少选择 1 项推送内容");
        var noOne = act(limin, "saveSubscription", Map.of("identity", "hosp", "frequency", "每月 5 日", "channel", "短信",
                "contents", List.of("告警汇总"), "recipients", List.of()));
        assertThat(noOne.getBody()).containsEntry("error", "请至少选择 1 名接收人");
        assertThat(act(limin, "saveSubscription", Map.of("identity", "conv", "frequency", "每月 5 日", "channel", "短信",
                "contents", List.of("告警汇总"), "recipients", List.of("专家组"))).getStatusCode().value()).isEqualTo(403);
        assertThat(act(limin, "saveSubscription", Map.of("identity", "hosp", "frequency", "每月 5 日", "channel", "传真",
                "contents", List.of("告警汇总"), "recipients", List.of("质控科"))).getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.sql("select count(*) from cockpit_subscription where owner = 'limin'").query(Long.class).single()).isZero();

        var saved = act(limin, "saveSubscription", ok);
        assertThat(saved.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> result = (Map<String, Object>) saved.getBody().get("result");
        assertThat(result).containsEntry("enabled", true).containsEntry("frequency", "每月 5 日").containsEntry("channel", "短信");

        // 新会话(重新登录)回显已保存的订阅
        String again = login("limin", "org");
        Map<String, Object> shown = (Map<String, Object>) identity(again, "hosp").get("savedSubscription");
        assertThat(shown).containsEntry("frequency", "每月 5 日").containsEntry("channel", "短信").containsEntry("enabled", true);
        assertThat((List<String>) shown.get("contents")).containsExactly("告警汇总", "待办提醒");
        assertThat((List<String>) shown.get("recipients")).containsExactly("李敏 · 医保办", "质控科");

        // 其他用户看不到这份订阅
        assertThat(identity(login("lihua", "bureau"), "conv").get("savedSubscription")).isNull();

        // 停用后如实显示
        var off = new java.util.HashMap<>(ok);
        off.put("enabled", false);
        assertThat(act(again, "saveSubscription", off).getStatusCode().value()).isEqualTo(200);
        assertThat((Map<String, Object>) identity(limin, "hosp").get("savedSubscription")).containsEntry("enabled", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void alarmAckIsSharedAcrossBureauSessionsButNotWithHospitals() {
        String lihua = login("lihua", "bureau");
        List<Map<String, Object>> before = (List<Map<String, Object>>) identity(lihua, "conv").get("alerts");
        assertThat(before).allSatisfy(a -> assertThat(a.get("acked")).isEqualTo(false));

        assertThat(act(lihua, "ackAlarm", Map.of("identity", "conv", "type", "预警", "text", "不存在的告警"))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(act(lihua, "ackAlarm", Map.of("identity", "conv", "type", "预警", "text", ALARM))
                .getStatusCode().value()).isEqualTo(200);
        // 重复确认幂等
        assertThat(act(lihua, "ackAlarm", Map.of("identity", "conv", "type", "预警", "text", ALARM))
                .getStatusCode().value()).isEqualTo(200);

        // 另一个医保局身份的新会话:同一条告警已确认,其他告警未确认
        List<Map<String, Object>> after = (List<Map<String, Object>>) identity(login("chenzy", "bureau"), "conv").get("alerts");
        assertThat(after).filteredOn(a -> ALARM.equals(a.get("text"))).singleElement().satisfies(a -> assertThat(a.get("acked")).isEqualTo(true));
        assertThat(after).filteredOn(a -> !ALARM.equals(a.get("text"))).allSatisfy(a -> assertThat(a.get("acked")).isEqualTo(false));

        // 医院视角的告警各自独立
        String limin = login("limin", "org");
        List<Map<String, Object>> hosp = (List<Map<String, Object>>) identity(limin, "hosp").get("alerts");
        assertThat(hosp).allSatisfy(a -> assertThat(a.get("acked")).isEqualTo(false));
        assertThat(act(limin, "ackAlarm", Map.of("identity", "hosp", "type", "提醒函", "text", "IU29 例均差额超阈值 · 待回执"))
                .getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.sql("select count(*) from cockpit_alarm_ack where scope = 'org:H001'").query(Long.class).single()).isEqualTo(1L);
    }
}
