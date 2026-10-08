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
 * 发布工作流 → 定向发布 → 机构签收, against a real database (identities via X-YB-User, dev-header mode):
 * role gates, 未批准不外发, persisted scope / log / 催办, per-institution sign-off, 更正 / 撤回, resetDemo rules
 * and A9 流程版本.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "YB_IT_DB_URL", matches = ".+")
@SuppressWarnings({"unchecked", "rawtypes"})
class PublishSignoffIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("YB_IT_DB_URL"));
        DevHeader.enable(r);
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcClient jdbc;

    static final String CONVENER = "chenzy";
    static final String ADMIN = "lihua";
    static final String HOSPITAL = "limin";       // H001 示例市第一人民医院
    static final String OTHER_HOSPITAL = "it-h002"; // H002 示例市第二人民医院 (created below)

    private ResponseEntity<Map> post(String user, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        if (user != null) h.set(DevHeader.HEADER, user);
        return http.exchange("/api/v1/actions/" + path, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
    }

    private Map<String, Object> page(String user, String code) {
        HttpHeaders h = new HttpHeaders();
        h.set(DevHeader.HEADER, user);
        ResponseEntity<Map> r = http.exchange("/api/v1/pages/" + code, HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("GET /pages/" + code + " as " + user).isTrue();
        return r.getBody();
    }

    private Map<String, Object> task(String id) {
        return ((List<Map<String, Object>>) page(CONVENER, "A8").get("tasks")).stream()
                .filter(t -> id.equals(t.get("id"))).findFirst().orElseThrow();
    }

    private Map<String, Object> b4Report(String user, String name) {
        return ((List<Map<String, Object>>) page(user, "B4").get("reports")).stream()
                .filter(r -> name.equals(r.get("name"))).findFirst().orElse(null);
    }

    static final String AUG = "2026年8月 DRG月度运行报告";

    @BeforeEach
    void reset() {
        jdbc.sql("""
                insert into app_user (login, name, role_code, org_id) values ('it-h002', '二院测试', 'hospital', 'H002')
                on conflict (login) do nothing""").update();
        jdbc.sql("""
                insert into user_identity (user_id, role_code, org_id, label, description, zone, tone, initial, target, scope, sort_order)
                select id, 'hospital', 'H002', '定点医药机构 · 示例市第二人民医院', 'it', '发布区', 'ok', '院', 'B1', '本院具名', 0
                from app_user u where login = 'it-h002' and not exists (select 1 from user_identity i where i.user_id = u.id)""").update();
        assertThat(post(CONVENER, "A8/resetDemo", Map.of("taskId", "m8")).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @AfterEach
    void restore() {
        post(CONVENER, "A8/resetDemo", Map.of("taskId", "m8"));
        jdbc.sql("update publish_task set step = 3, status = 'open' where id = 'br25'").update();
        jdbc.sql("delete from publish_event where task_id = 'br25'").update();
    }

    @Test
    void approvalIsConvenerOnlyAndNeedsAScope() {
        assertThat(post(ADMIN, "A8/approvePublish", Map.of("taskId", "m8", "institutions", List.of("第一人民医院")))
                .getStatusCode().value()).isEqualTo(403);
        assertThat(post(HOSPITAL, "A8/approvePublish", Map.of("taskId", "m8", "institutions", List.of("第一人民医院")))
                .getStatusCode().value()).isEqualTo(403);
        ResponseEntity<Map> zero = post(CONVENER, "A8/approvePublish", Map.of("taskId", "m8", "institutions", List.of()));
        assertThat(zero.getStatusCode().value()).isEqualTo(400);
        assertThat((String) zero.getBody().get("error")).contains("0 家");
        assertThat(post(CONVENER, "A8/approvePublish", Map.of("taskId", "m8", "institutions", List.of("不存在医院")))
                .getStatusCode().value()).isEqualTo(400);
        assertThat(((Number) task("m8").get("step")).intValue()).isEqualTo(5);
    }

    @Test
    void approvedScopeAndLogSurviveAReload() {
        ResponseEntity<Map> ok = post(CONVENER, "A8/approvePublish", Map.of("taskId", "m8", "comment", "it 同意",
                "institutions", List.of("第一人民医院", "第二人民医院", "市中医院"), "filters", Map.of("tiers", List.of(0))));
        assertThat(ok.getStatusCode().is2xxSuccessful()).as("approve → " + ok.getBody()).isTrue();
        Map<String, Object> t = task("m8");
        assertThat(((Number) t.get("step")).intValue()).isEqualTo(6);
        Map<String, Object> scope = (Map<String, Object>) t.get("scope");
        assertThat(((Number) scope.get("coverage")).intValue()).isEqualTo(3);
        List<Map<String, Object>> logs = (List<Map<String, Object>>) t.get("logs");
        assertThat(logs).anySatisfy(l -> {
            assertThat(l.get("who")).isEqualTo("陈志远");
            assertThat((String) l.get("what")).contains("it 同意");
        });
        assertThat(logs).anySatisfy(l -> assertThat((String) l.get("what")).contains("推送至 3 家机构"));
        assertThat((List<String>) t.get("signed")).isEmpty();

        // 催办 is recorded once per institution and comes back with the task
        assertThat(post(ADMIN, "A8/urgeSign", Map.of("taskId", "m8", "institution", "市中医院")).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(post(ADMIN, "A8/urgeSign", Map.of("taskId", "m8", "institution", "第五医院")).getStatusCode().value()).isEqualTo(400);
        assertThat((List<String>) task("m8").get("urged")).containsExactly("市中医院");

        // a stale second approval fails with a Chinese message
        ResponseEntity<Map> again = post(CONVENER, "A8/approvePublish", Map.of("taskId", "m8", "institutions", List.of("第一人民医院")));
        assertThat(again.getStatusCode().value()).isEqualTo(400);
        assertThat((String) again.getBody().get("error")).contains("不在召集人审批节点");
    }

    @Test
    void rejectionCommentIsLoggedAndTheTaskCanBeResubmitted() {
        assertThat(post(CONVENER, "A8/rejectPublish", Map.of("taskId", "m8", "toStep", 3, "comment", "请补充县区解读"))
                .getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> t = task("m8");
        assertThat(((Number) t.get("step")).intValue()).isEqualTo(3);
        assertThat((List<Map<String, Object>>) t.get("logs")).anySatisfy(l -> assertThat((String) l.get("what")).contains("请补充县区解读"));
        assertThat(post(HOSPITAL, "A8/submitForApproval", Map.of("taskId", "m8")).getStatusCode().value()).isEqualTo(403);
        assertThat(post(ADMIN, "A8/submitForApproval", Map.of("taskId", "m8")).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(((Number) task("m8").get("step")).intValue()).isEqualTo(5);
        assertThat(post(ADMIN, "A8/submitForApproval", Map.of("taskId", "m8")).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void reportIsOnlyReleasedAfterApprovalAndSignedPerInstitution() {
        // 未批准不外发
        assertThat(b4Report(HOSPITAL, AUG)).isNull();
        assertThat(post(HOSPITAL, "B4/signReport", Map.of("reportId", "R-2026-08")).getStatusCode().value()).isEqualTo(400);

        assertThat(post(CONVENER, "A8/approvePublish", Map.of("taskId", "m8",
                "institutions", List.of("第一人民医院", "第二人民医院"))).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(b4Report(HOSPITAL, AUG)).containsEntry("status", "sign");

        // the bureau cannot sign on an institution's behalf
        assertThat(post(CONVENER, "B4/signReport", Map.of("name", AUG)).getStatusCode().value()).isEqualTo(403);
        assertThat(post(ADMIN, "D1/signReport", Map.of("reportId", "R-2026-08")).getStatusCode().value()).isEqualTo(403);
        assertThat(page(CONVENER, "B4").get("canSign")).isEqualTo(false);

        assertThat(post(HOSPITAL, "D1/signReport", Map.of("reportId", "R-2026-08")).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(post(HOSPITAL, "B4/signReport", Map.of("reportId", "R-2026-08")).getStatusCode().value()).isEqualTo(400);
        assertThat(b4Report(HOSPITAL, AUG)).containsEntry("status", "signed");
        Map<String, Object> d1 = page(HOSPITAL, "D1");
        assertThat((Map<String, Object>) d1.get("report")).containsEntry("status", "signed").containsEntry("id", "R-2026-08");
        assertThat((List<Map<String, Object>>) ((Map<String, Object>) page(HOSPITAL, "B1").get("reportSignoff")).get("pendingReports"))
                .noneSatisfy(r -> assertThat(r.get("id")).isEqualTo("R-2026-08"));

        // another institution still has it pending — sign-off is per institution
        assertThat(b4Report(OTHER_HOSPITAL, AUG)).containsEntry("status", "sign");
        assertThat(((Map<String, Object>) page(OTHER_HOSPITAL, "D1").get("report"))).containsEntry("status", "sign");
        assertThat((List<Map<String, Object>>) ((Map<String, Object>) page(OTHER_HOSPITAL, "B1").get("reportSignoff")).get("pendingReports"))
                .anySatisfy(r -> assertThat(r.get("id")).isEqualTo("R-2026-08"));

        // A8 签收追踪 counts the real sign-off
        assertThat((List<String>) task("m8").get("signed")).containsExactly("第一人民医院");
    }

    @Test
    void reportOutsideTheApprovedScopeIsNotServed() {
        assertThat(post(CONVENER, "A8/approvePublish", Map.of("taskId", "m8", "institutions", List.of("第二人民医院")))
                .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(b4Report(HOSPITAL, AUG)).isNull();
        assertThat(b4Report(OTHER_HOSPITAL, AUG)).isNotNull();
        assertThat(post(HOSPITAL, "D1/signReport", Map.of("reportId", "R-2026-08")).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void reportFeedbackBecomesAnA10Item() {
        assertThat(post(CONVENER, "B4/submitFeedback", Map.of("reportId", "R-2026-07", "text", "医保局代提意见")).getStatusCode().value()).isEqualTo(403);
        assertThat(post(HOSPITAL, "B4/submitFeedback", Map.of("reportId", "R-2026-07", "text", " ")).getStatusCode().value()).isEqualTo(400);
        ResponseEntity<Map> r = post(HOSPITAL, "B4/submitFeedback", Map.of("reportId", "R-2026-07", "section", "三、病组结构与偏离贡献",
                "text", "IT:建议补充 GG19 病例数口径说明"));
        assertThat(r.getStatusCode().is2xxSuccessful()).as("submitFeedback → " + r.getBody()).isTrue();
        String id = (String) ((Map<String, Object>) r.getBody().get("result")).get("id");
        assertThat(jdbc.sql("select org_name || '|' || report || '|' || kind from feedback_item where id = :id").param("id", id)
                .query(String.class).single()).isEqualTo("示例市第一人民医院|2026年7月 DRG月度运行报告|意见");
        jdbc.sql("delete from feedback_item where id = :id").param("id", id).update();
    }

    @Test
    void correctionAndWithdrawOnlyForPublishedTasks() {
        assertThat(post(ADMIN, "A8/startWithdraw", Map.of("taskId", "w8")).getStatusCode().value()).isEqualTo(400);
        assertThat(post(CONVENER, "A8/approvePublish", Map.of("taskId", "m8", "institutions", List.of("第一人民医院")))
                .getStatusCode().is2xxSuccessful()).isTrue();
        ResponseEntity<Map> r = post(ADMIN, "A8/startCorrection", Map.of("taskId", "m8", "reason", "补传数据"));
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        String id = (String) ((Map<String, Object>) r.getBody().get("result")).get("taskId");
        Map<String, Object> derived = task(id);
        assertThat(derived).containsEntry("group", "更正").containsEntry("origin", "m8");
        assertThat(((Number) derived.get("step")).intValue()).isEqualTo(4);
        // only one open revision at a time
        assertThat(post(ADMIN, "A8/startWithdraw", Map.of("taskId", "m8")).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void resetDemoIsConvenerOnlyAndNeverReopensArchivedOrOrdinaryTasks() {
        assertThat(post(ADMIN, "A8/resetDemo", Map.of("taskId", "m8")).getStatusCode().value()).isEqualTo(403);
        assertThat(post(CONVENER, "A8/resetDemo", Map.of("taskId", "y25")).getStatusCode().value()).isEqualTo(400);
        assertThat(post(CONVENER, "A8/resetDemo", Map.of("taskId", "br25")).getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.sql("select step from publish_task where id = 'y25'").query(Integer.class).single()).isEqualTo(10);
    }

    @Test
    void a10CorrectionTasksAppearInA8WithLiveDueText() {
        jdbc.sql("insert into publish_task (id, title, kind, step, due, status) values ('GZ-YJ-IT', '更正 · YJ-IT', '更正', 1, :due, 'open') on conflict do nothing")
                .param("due", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(1)).update();
        try {
            Map<String, Object> t = task("GZ-YJ-IT");
            assertThat(t).containsEntry("group", "更正").containsEntry("due", "逾期 1 天");
            assertThat(((Number) task("m8").get("step")).intValue()).isEqualTo(5);
        } finally {
            jdbc.sql("delete from publish_task where id = 'GZ-YJ-IT'").update();
        }
    }

    @Test
    void flowVersionIsPersistedAndValidated() {
        Map<String, Object> a9 = page(ADMIN, "A9");
        Map<String, Object> flow = ((List<Map<String, Object>>) a9.get("flows")).stream()
                .filter(f -> "预警".equals(f.get("name"))).findFirst().orElseThrow();
        int v = ((Number) flow.get("version")).intValue();
        List<Map<String, Object>> nodes = List.of(
                Map.of("lane", "行政管理组", "col", 0, "name", "预警确认", "kind", "人工", "days", 1, "timeoutAction", "提醒承办人", "channels", List.of("站内信")),
                Map.of("lane", "召集人", "col", 1, "name", "审批", "kind", "审批", "days", 1),
                Map.of("lane", "定点医疗机构", "col", 2, "name", "回执", "kind", "签收", "days", 8),
                Map.of("lane", "行政管理组", "col", 3, "name", "复查", "kind", "自动", "days", 0));
        // approval handled by a hospital is refused
        List<Map<String, Object>> bad = List.of(nodes.get(0),
                Map.of("lane", "定点医疗机构", "col", 1, "name", "审批", "kind", "审批", "days", 1), nodes.get(2), nodes.get(3));
        assertThat(post(ADMIN, "A9/publishFlowVersion", Map.of("flow", "预警", "version", v + 1, "nodes", bad)).getStatusCode().value()).isEqualTo(400);
        assertThat(post(HOSPITAL, "A9/publishFlowVersion", Map.of("flow", "预警", "version", v + 1, "nodes", nodes)).getStatusCode().value()).isEqualTo(403);
        ResponseEntity<Map> ok = post(ADMIN, "A9/publishFlowVersion", Map.of("flow", "预警", "version", v + 1, "nodes", nodes, "note", "回执时限 10 → 8 天"));
        assertThat(ok.getStatusCode().is2xxSuccessful()).as("publish → " + ok.getBody()).isTrue();
        // stale editor (same version again) is refused
        assertThat(post(ADMIN, "A9/publishFlowVersion", Map.of("flow", "预警", "version", v + 1, "nodes", nodes)).getStatusCode().value()).isEqualTo(400);
        Map<String, Object> after = ((List<Map<String, Object>>) page(ADMIN, "A9").get("flows")).stream()
                .filter(f -> "预警".equals(f.get("name"))).findFirst().orElseThrow();
        assertThat(((Number) after.get("version")).intValue()).isEqualTo(v + 1);
        assertThat((List<Map<String, Object>>) after.get("nodes")).anySatisfy(n -> {
            assertThat(n.get("name")).isEqualTo("回执");
            assertThat(((Number) n.get("days")).intValue()).isEqualTo(8);
        });
        assertThat((List<Map<String, Object>>) after.get("versions")).first().satisfies(x -> {
            assertThat((String) x.get("label")).isEqualTo("v" + (v + 1) + " · 当前");
            assertThat((String) x.get("note")).contains("回执时限 10 → 8 天");
        });
    }
}
