package cn.ybdata.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * 意见 / 预警 state machines and institution isolation (FLOW-01/11/13/16/17, HOSP-02/03/14/22/23, API P0-4/5):
 *   YB_IT_DB_URL=jdbc:postgresql://localhost:5432/ybdata_test mvn verify -Dtest='*IT' -Dsurefire.failIfNoSpecifiedTests=false
 * Uses dev-header identities (known logins are access-checked exactly like a session).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "YB_IT_DB_URL", matches = ".+")
@SuppressWarnings({"rawtypes", "unchecked"})
class FeedbackAlertIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("YB_IT_DB_URL"));
        r.add("yb.auth.dev-header", () -> "true");
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcClient jdbc;

    private ResponseEntity<Map> post(String user, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-YB-User", user);
        return http.exchange("/api/v1/actions/" + path, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
    }

    private Map<String, Object> page(String user, String code) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-YB-User", user);
        ResponseEntity<Map> r = http.exchange("/api/v1/pages/" + code, HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("GET /pages/" + code + " as " + user).isTrue();
        return r.getBody();
    }

    private static int status(ResponseEntity<Map> r) {
        return r.getStatusCode().value();
    }

    private String newItem(String org, String orgId) {
        String id = "YJ-T" + System.nanoTime() % 100000000L;
        jdbc.sql("insert into feedback_item (id, kind, org_name, org_id, title, status, body) values (:id, '意见', :o, :oid, 'IT 意见', 'todo', 'x')")
                .param("id", id).param("o", org).param("oid", orgId).update();
        return id;
    }

    @BeforeEach
    void reset() {
        jdbc.sql("update alert set status = 'sent', sent_at = now() where id = 'AL-07'").update();
        jdbc.sql("update alert set status = 'unsent', sent_at = null where id = 'AL-05'").update();
        jdbc.sql("delete from alert_receipt where alert_id in ('AL-05', 'AL-07')").update();
        jdbc.sql("delete from verification_submission").update();
        jdbc.sql("update verification_round set deadline = now() + interval '3 days'").update();
    }

    // ── FLOW-01 / API P0-5: receipts are per institution and follow the state machine ──

    @Test
    void receiptOnlyForOwnSentAlert() {
        Map<String, String> iu29 = Map.of("alert", "IU29 例均基金差额超阈值", "category", "编码调整", "text", "已完成编码培训");
        // H001 may not answer the 丙区第2医院 alert, nor one never sent
        assertThat(status(post("limin", "D1/submitReceipt", Map.of("alert", "医保外费用占比 超阈值", "category", "其他", "text", "x")))).isEqualTo(403);
        assertThat(jdbc.sql("select status from alert where id = 'AL-05'").query(String.class).single()).isEqualTo("unsent");
        // required fields: empty alert → 400 (not 500), empty text → 400
        assertThat(status(post("limin", "D1/submitReceipt", Map.of("alert", "")))).isEqualTo(400);
        assertThat(status(post("limin", "D1/submitReceipt", Map.of()))).isEqualTo(400);
        assertThat(status(post("limin", "D1/submitReceipt", Map.of("alert", "IU29 例均基金差额超阈值", "category", "编码调整", "text", " ")))).isEqualTo(400);
        // the 医保局 cannot answer on an institution's behalf
        assertThat(status(post("chenzy", "D1/submitReceipt", iu29))).isEqualTo(403);

        ResponseEntity<Map> ok = post("limin", "D1/submitReceipt", iu29);
        assertThat(status(ok)).as("%s", ok.getBody()).isEqualTo(200);
        assertThat((Map<String, Object>) ok.getBody().get("result")).containsEntry("alertId", "AL-07");
        assertThat(jdbc.sql("select status from alert where id = 'AL-06'").query(String.class).single()).isNotEqualTo("ack");
        assertThat(jdbc.sql("select org_id from alert_receipt where alert_id = 'AL-07'").query(String.class).single()).isEqualTo("H001");
        // only once
        assertThat(status(post("limin", "D1/submitReceipt", iu29))).isEqualTo(400);

        // A11 shows the real receipt text
        List<Map<String, Object>> alerts = (List<Map<String, Object>>) page("lihua", "A11").get("alerts");
        Map<String, Object> al07 = alerts.stream().filter(a -> "AL-07".equals(a.get("id"))).findFirst().orElseThrow();
        assertThat(al07.get("status")).isEqualTo("ack");
        assertThat((Map<String, Object>) al07.get("receipt")).containsEntry("text", "已完成编码培训").containsEntry("category", "编码调整");
    }

    // ── FLOW-01 / API P1: sendReminder only for existing (or truly raised) alerts ──

    @Test
    void reminderCannotForgeAlerts() {
        assertThat(status(post("lihua", "A11/sendReminder", Map.of("alertId", "QA-FLOW-FAKE", "org", "伪造机构")))).isEqualTo(400);
        assertThat(status(post("lihua", "A11/sendReminder", Map.of("alertId", "AL-R08-H001", "org", "伪造机构")))).isEqualTo(400); // rule quiet
        assertThat(status(post("lihua", "A11/sendReminder", Map.of("alertId", "AL-R06-H999")))).isEqualTo(400);
        assertThat(jdbc.sql("select count(*) from alert where id in ('QA-FLOW-FAKE', 'AL-R08-H001', 'AL-R06-H999')").query(Long.class).single()).isZero();
        // an existing alert: unsent → sent once
        assertThat(status(post("lihua", "A11/sendReminder", Map.of("alertId", "AL-05", "org", "伪造机构")))).isEqualTo(200);
        assertThat(status(post("lihua", "A11/sendReminder", Map.of("alertId", "AL-05")))).isEqualTo(400);
        assertThat(jdbc.sql("select org_name from alert where id = 'AL-05'").query(String.class).single()).isEqualTo("丙区第2医院");
        // an alert the rule engine really raises gets its row from the rule + org table, not from the client
        jdbc.sql("delete from alert where id = 'AL-R08-H002'").update();
        jdbc.sql("""
                insert into indicator_series (org_id, metric, period, value)
                select 'H002', 'split_stay_ratio', max(period), 4.2 from indicator_series
                on conflict (org_id, metric, period) do update set value = 4.2""").update();
        try {
            assertThat(status(post("lihua", "A11/sendReminder", Map.of("alertId", "AL-R08-H002", "org", "伪造机构", "metric", "伪造")))).isEqualTo(200);
            var row = jdbc.sql("select org_name, metric, status from alert where id = 'AL-R08-H002'")
                    .query((rs, i) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3))).single();
            assertThat(row).containsExactly("示例市第二人民医院", "分解住院疑似率", "sent");
        } finally {
            jdbc.sql("delete from alert where id = 'AL-R08-H002'").update();
            jdbc.sql("delete from indicator_series where org_id = 'H002' and metric = 'split_stay_ratio'").update();
        }
    }

    // ── API P0-4: a county identity sees (and reminds) only its own district ──

    @Test
    void countySeesOnlyItsDistrict() {
        jdbc.sql("""
                insert into app_user (login, name, role_code, org_id) values ('it_cty', 'IT县区', 'county', 'H010')
                on conflict (login) do nothing""").update();
        jdbc.sql("""
                insert into user_identity (user_id, role_code, org_id, label, description, zone, tone, initial, target, scope, sort_order)
                select id, 'county', 'H010', '甲县医保', '本县具名', '发布区', 'ok', '县', 'A11', '甲县', 0 from app_user u
                where login = 'it_cty' and not exists (select 1 from user_identity i where i.user_id = u.id)""").update();
        List<Map<String, Object>> alerts = (List<Map<String, Object>>) page("it_cty", "A11").get("alerts");
        assertThat(alerts).isNotEmpty().allSatisfy(a -> assertThat(a.get("org")).isEqualTo("甲县人民医院"));
        assertThat(status(post("it_cty", "A11/sendReminder", Map.of("alertId", "AL-05")))).isEqualTo(403);
    }

    // ── FLOW-11 / FLOW-16: A10 state machine and real track ──

    @Test
    void feedbackStateMachine() {
        String id = newItem("甲县人民医院", "H010");
        assertThat(status(post("wangq", "A10/assignFeedback", Map.of("id", id, "assignee", "")))).isEqualTo(400);
        assertThat(status(post("wangq", "A10/assignFeedback", Map.of("id", id, "assignee", "不存在的人")))).isEqualTo(400);
        assertThat(status(post("wangq", "A10/assignFeedback", Map.of("id", id, "assignee", "张悦")))).isEqualTo(200);
        assertThat(status(post("wangq", "A10/replyFeedback", Map.of("id", id, "text", "  ")))).isEqualTo(400);
        assertThat(status(post("wangq", "A10/replyFeedback", Map.of("id", id, "text", "已核实,口径说明见指标卡。")))).isEqualTo(200);
        // closed: no second reply, no re-assignment
        assertThat(status(post("wangq", "A10/replyFeedback", Map.of("id", id, "text", "再答复")))).isEqualTo(400);
        assertThat(status(post("wangq", "A10/assignFeedback", Map.of("id", id, "assignee", "李华")))).isEqualTo(400);
        assertThat(jdbc.sql("select count(*) from feedback_reply where item_id = :id").param("id", id).query(Long.class).single()).isEqualTo(1);

        Map<String, Object> a10 = page("wangq", "A10");
        Map<String, Object> it = ((List<Map<String, Object>>) a10.get("items")).stream()
                .filter(x -> id.equals(x.get("id"))).findFirst().orElseThrow();
        Map<String, Object> track = (Map<String, Object>) it.get("track");
        assertThat(it.get("status")).isEqualTo("done");
        assertThat(track).containsEntry("assignedBy", "王倩").containsEntry("repliedBy", "王倩")
                .containsEntry("reply", "已核实,口径说明见指标卡。");
        assertThat((Map<String, Object>) a10.get("stats")).containsKeys("replyRate", "avgReplyDays", "corrections");
    }

    // ── FLOW-13 / HOSP-02/03/14/22/23: B5 ──

    private static Map<String, Object> diff(String id, String own, String reason) {
        return Map.of("id", id, "result", "diff", "ownValue", own, "reason", reason);
    }

    private static List<Object> allOk() {
        return List.of(Map.of("id", "cases", "result", "ok"), Map.of("id", "fundDiff", "result", "ok"),
                Map.of("id", "comorbidity", "result", "ok"), Map.of("id", "los", "result", "ok"));
    }

    @Test
    void verificationRules() {
        List<Object> full = List.of(Map.of("id", "cases", "result", "ok"),
                Map.of("id", "fundDiff", "result", "diff", "ownValue", "+1,980 元", "reason", "12 例特例单议未剔除",
                        "attachments", List.of(Map.of("name", "特例单议批复.pdf", "size", 1024, "type", "application/pdf"))),
                Map.of("id", "comorbidity", "result", "ok"), Map.of("id", "los", "result", "ok"));
        // the 医保局 cannot submit for an institution
        assertThat(status(post("chenzy", "B5/submitVerification", Map.of("items", full)))).isEqualTo(403);
        // 有差异 needs value and reason; unknown ids and incomplete sets are refused
        assertThat(status(post("limin", "B5/submitVerification", Map.of("items", List.of(
                Map.of("id", "cases", "result", "ok"), diff("fundDiff", "", ""),
                Map.of("id", "comorbidity", "result", "ok"), Map.of("id", "los", "result", "ok")))))).isEqualTo(400);
        assertThat(status(post("limin", "B5/submitVerification", Map.of("items", List.of(Map.of("id", "QA-HOSP-bureau", "result", "ok")))))).isEqualTo(400);
        assertThat(status(post("limin", "B5/submitVerification", Map.of("items", List.of(Map.of("id", "cases", "result", "ok")))))).isEqualTo(400);

        ResponseEntity<Map> ok = post("limin", "B5/submitVerification", Map.of("items", full));
        assertThat(status(ok)).as("%s", ok.getBody()).isEqualTo(200);
        String created = ((List<String>) ((Map<String, Object>) ok.getBody().get("result")).get("created")).get(0);
        var row = jdbc.sql("select org_id, title, attachments::text from feedback_item where id = :id").param("id", created)
                .query((rs, i) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3))).single();
        assertThat(row.get(0)).isEqualTo("H001");
        assertThat(row.get(1)).isEqualTo("核对差异 · BR25 例均基金差额");
        assertThat(row.get(2)).contains("特例单议批复.pdf");
        // once per institution and round
        assertThat(status(post("limin", "B5/submitVerification", Map.of("items", allOk())))).isEqualTo(400);

        Map<String, Object> b5 = page("limin", "B5");
        assertThat(b5).containsKey("submission");
        assertThat((Map<String, Object>) b5.get("viewer")).containsEntry("canSubmit", false);
        assertThat((List<Map<String, Object>>) b5.get("progress")).anySatisfy(p -> assertThat(p.get("id")).isEqualTo(created));

        // past the deadline nothing is accepted
        jdbc.sql("delete from verification_submission").update();
        jdbc.sql("update verification_round set deadline = now() - interval '1 day'").update();
        assertThat(status(post("limin", "B5/submitVerification", Map.of("items", allOk())))).isEqualTo(400);
        Map<String, Object> closed = page("limin", "B5");
        assertThat((Map<String, Object>) closed.get("round")).containsEntry("closed", true);
        assertThat(closed.get("remainingDays")).isEqualTo(0);
    }

    @Test
    void concurrentSubmissionsDoNotCollide() throws Exception {
        // feedback ids come from a sequence: parallel inserts from different sources never clash
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Callable<ResponseEntity<Map>>> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String text = "IT 并发监督建议第 " + i + " 条 · " + System.nanoTime();
            calls.add(() -> post("zhoumin", "C3/submitSuggestion", Map.of("topic", "公开内容", "text", text)));
        }
        calls.add(() -> post("limin", "B5/submitVerification", Map.of("items", List.of(
                Map.of("id", "cases", "result", "ok"), diff("fundDiff", "+2,000 元", "并发测试"),
                diff("comorbidity", "70%", "并发测试"), Map.of("id", "los", "result", "ok")))));
        calls.add(() -> post("limin", "B5/submitVerification", Map.of("items", allOk())));
        List<Integer> codes = new ArrayList<>();
        for (Future<ResponseEntity<Map>> f : pool.invokeAll(calls)) codes.add(status(f.get()));
        pool.shutdown();
        assertThat(codes).doesNotContain(500);
        assertThat(codes.subList(0, 4)).containsOnly(200);
        // exactly one of the two B5 submissions of the same institution wins
        assertThat(codes.subList(4, 6)).containsExactlyInAnyOrder(200, 400);
    }

    // ── FLOW-04: C3 监督建议 is stored, deduplicated and visible to its author ──

    @Test
    void oversightSuggestion() {
        String text = "建议在公开日历中标注每期发布的具体日期 " + System.nanoTime();
        assertThat(status(post("zhoumin", "C3/submitSuggestion", Map.of()))).isEqualTo(400);
        assertThat(status(post("zhoumin", "C3/submitSuggestion", Map.of("topic", "公开内容", "text", "太短")))).isEqualTo(400);
        assertThat(status(post("zhoumin", "C3/submitSuggestion", Map.of("topic", "公开内容", "text", text)))).isEqualTo(200);
        assertThat(status(post("zhoumin", "C3/submitSuggestion", Map.of("topic", "公开内容", "text", text)))).isEqualTo(400);
        List<Map<String, Object>> mine = (List<Map<String, Object>>) page("zhoumin", "C3").get("mySuggestions");
        assertThat(mine).anySatisfy(m -> assertThat(m.get("text")).isEqualTo(text));
        List<Map<String, Object>> a10 = (List<Map<String, Object>>) page("wangq", "A10").get("items");
        assertThat(a10).anySatisfy(m -> assertThat(m.get("text")).isEqualTo(text));
    }
}
