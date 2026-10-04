package cn.ybdata.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A14 audit API against a real PostgreSQL database:
 *   YB_IT_DB_URL=jdbc:postgresql://localhost:5432/ybdata_test mvn verify -Dtest=AuditApiIT
 * Every test works on its own actor name, so it tolerates rows left by other tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "YB_IT_DB_URL", matches = ".+")
class AuditApiIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("YB_IT_DB_URL"));
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcClient jdbc;
    @Autowired AuditService audit;
    @Autowired TransactionTemplate tx;

    private static String uniqueActor() {
        return "it" + UUID.randomUUID().toString().substring(0, 8);
    }

    private long act(String actor, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-YB-User", actor);
        h.set("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/128.0.0.0 Safari/537.36");
        ResponseEntity<Map> r = http.exchange("/api/v1/actions/" + path, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as(path + " → " + r.getBody()).isTrue();
        return ((Number) r.getBody().get("auditId")).longValue();
    }

    /** Append a correctly chained event with a chosen timestamp (record() always uses now()). */
    private long appendAt(OffsetDateTime at, String actor, String page, String action, String payload) {
        return tx.execute(s -> {
            jdbc.sql("select pg_advisory_xact_lock(7311)").query().singleRow();
            String prev = jdbc.sql("select hash from audit_event order by id desc limit 1").query(String.class)
                    .optional().orElse(AuditService.GENESIS);
            OffsetDateTime t = at.truncatedTo(ChronoUnit.MICROS);
            String hash = AuditService.chainHash(prev, t, actor, page, action, payload);
            return jdbc.sql("""
                    insert into audit_event (at, actor, page, action, payload, terminal, prev_hash, hash)
                    values (:at, :a, :p, :ac, cast(:pl as jsonb), '10.0.0.1 · Chrome 128', :prev, :h) returning id""")
                    .param("at", t).param("a", actor).param("p", page).param("ac", action).param("pl", payload)
                    .param("prev", prev).param("h", hash).query(Long.class).single();
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(String query) {
        Map<String, Object> page = http.getForObject("/api/v1/audit?" + query, Map.class);
        return (List<Map<String, Object>>) page.get("items");
    }

    @Test
    void listMapsTypesDerivesFieldsAndFilters() {
        String me = uniqueActor();
        act(me, "A8/urgeSign", Map.of("taskId", "m8", "institution", "示例市第一人民医院"));
        act(me, "B4/exportReport", Map.of("name", "2026年8月 DRG月度运行报告", "version", "v1", "format", "pdf"));
        act(me, "D1/signReport", Map.of("report", "2026年8月 本院医保运行报告", "version", "v1"));

        List<Map<String, Object>> all = items("actor=" + me);
        assertThat(all).extracting(m -> m.get("action")).containsExactly("signReport", "exportReport", "urgeSign");
        assertThat(all).extracting(m -> m.get("type")).containsExactly("审批", "导出", "发布");
        Map<String, Object> sign = all.get(0);
        assertThat(sign.get("object")).isEqualTo("签收报告 · 2026年8月 本院医保运行报告");
        assertThat(sign.get("chain")).isEqualTo("ok");
        // ActionController (auth-owned) stores "ip · first 40 UA chars", so only presence is asserted here
        assertThat((String) sign.get("terminal")).isNotBlank();
        assertThat((String) sign.get("ip")).isNotBlank();
        assertThat((String) sign.get("watermark")).matches("WM-[0-9A-F]{4}-[0-9A-F]{4}");
        assertThat((String) sign.get("time")).matches("\\d\\d:\\d\\d:\\d\\d");

        assertThat(items("actor=" + me + "&type=导出")).extracting(m -> m.get("action")).containsExactly("exportReport");
        assertThat(items("actor=" + me + "&page=A8")).extracting(m -> m.get("action")).containsExactly("urgeSign");
        assertThat(items("actor=" + me + "&action=signReport")).hasSize(1);
        assertThat(items("actor=" + me + "&from=2000-01-01&to=2000-01-02")).isEmpty();

        // login names resolve to the user's display name and role
        // (written directly: the auditor identity is read-only and may not post actions)
        appendAt(OffsetDateTime.now(), "zhaoan", "A14", "viewPage", "{}");
        assertThat(items("actor=zhaoan&limit=1")).singleElement().satisfies(m -> {
            assertThat(m.get("who")).isEqualTo("赵安");
            assertThat(m.get("role")).isEqualTo("安全审计员");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void cursorPagination() {
        String me = uniqueActor();
        for (int i = 0; i < 5; i++) act(me, "D1/markRead", Map.of("message", "m" + i));
        Map<String, Object> p1 = http.getForObject("/api/v1/audit?actor=" + me + "&limit=2", Map.class);
        List<Map<String, Object>> i1 = (List<Map<String, Object>>) p1.get("items");
        assertThat(i1).hasSize(2);
        Number cursor = (Number) p1.get("nextCursor");
        assertThat(cursor.longValue()).isEqualTo(((Number) i1.get(1).get("id")).longValue());
        assertThat(((Number) p1.get("today")).longValue()).isGreaterThanOrEqualTo(5);

        Map<String, Object> p2 = http.getForObject("/api/v1/audit?actor=" + me + "&limit=2&cursor=" + cursor, Map.class);
        Map<String, Object> p3 = http.getForObject("/api/v1/audit?actor=" + me + "&limit=2&cursor=" + p2.get("nextCursor"), Map.class);
        assertThat((List<?>) p3.get("items")).hasSize(1);
        assertThat(p3.get("nextCursor")).isNull();
        // pages are disjoint and newest first
        long last1 = ((Number) i1.get(1).get("id")).longValue();
        long first2 = ((Number) ((List<Map<String, Object>>) p2.get("items")).get(0).get("id")).longValue();
        assertThat(first2).isLessThan(last1);
    }

    @Test
    void offHoursFilterAndRiskFlag() {
        String me = uniqueActor();
        OffsetDateTime night = OffsetDateTime.of(2026, 9, 30, 2, 14, 36, 0, ZoneOffset.ofHours(8));
        OffsetDateTime day = OffsetDateTime.of(2026, 9, 30, 9, 12, 4, 0, ZoneOffset.ofHours(8));
        appendAt(night, me, "B1", "viewPage", "{}");
        appendAt(day, me, "B1", "viewPage", "{}");
        appendAt(night.plusHours(21), me, "A13", "setPolicyRule", "{\"key\":\"minOrg\",\"value\":6}"); // 23:14

        List<Map<String, Object>> off = items("actor=" + me + "&offHours=true");
        assertThat(off).hasSize(2).allSatisfy(m -> assertThat(m.get("offHours")).isEqualTo(true));
        assertThat(off).filteredOn(m -> "viewPage".equals(m.get("action"))).singleElement()
                .satisfies(m -> assertThat(m.get("risk")).isEqualTo(true));
        assertThat(off).filteredOn(m -> "setPolicyRule".equals(m.get("action"))).singleElement()
                .satisfies(m -> assertThat(m.get("risk")).isEqualTo(false));
        assertThat(items("actor=" + me + "&offHours=false")).singleElement()
                .satisfies(m -> assertThat(m.get("offHours")).isEqualTo(false));
        assertThat(items("actor=" + me + "&from=2026-09-30T09:00:00&to=2026-09-30T10:00:00")).hasSize(1);
        assertThat(items("actor=" + me + "&from=2026-09-30&to=2026-09-30")).hasSize(3);
        assertThat(items("actor=" + me + "&type=查阅")).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailCarriesHashesWatermarkAndSettingDiff() {
        String me = uniqueActor();
        // A13: previous value of the same rule, not of another rule
        long a = act(me, "A13/setPolicyRule", Map.of("key", "minCase", "value", 35));
        act(me, "A13/setPolicyRule", Map.of("key", "wm", "value", false));
        long c = act(me, "A13/setPolicyRule", Map.of("key", "minCase", "value", 40));
        Map<String, Object> d = http.getForObject("/api/v1/audit/" + c, Map.class);
        assertThat(d.get("type")).isEqualTo("配置");
        assertThat((Map<String, Object>) d.get("diff")).containsEntry("from", "小样本抑制 · 病组病例数 35 例")
                .containsEntry("to", "小样本抑制 · 病组病例数 40 例");
        assertThat(((String) d.get("prevHash"))).hasSize(64);
        assertThat(((String) d.get("hash"))).hasSize(64);
        assertThat(d.get("watermark")).isEqualTo(AuditService.watermark((String) d.get("hash")));
        assertThat(d.get("ip")).isNotNull();
        assertThat((Map<String, Object>) d.get("payload")).containsEntry("key", "minCase");
        assertThat(http.getForObject("/api/v1/audit/" + a, Map.class).get("diff")).isNotNull();

        // A15: field-level diff against the previous publish
        act(me, "A15/publishAppearance", Map.of("c", 0, "dens", 1, "rad", 1, "font", 1, "card", 1, "scr", 0,
                "mot", 1, "rot", 20, "wm", 1, "name", "医保数据公开 · 定向发布平台"));
        long p = act(me, "A15/publishAppearance", Map.of("c", 1, "dens", 1, "rad", 1, "font", 2, "card", 1, "scr", 0,
                "mot", 1, "rot", 20, "wm", 1, "name", "医保数据公开 · 定向发布平台"));
        Map<String, Object> pd = (Map<String, Object>) http.getForObject("/api/v1/audit/" + p, Map.class).get("diff");
        assertThat((List<Map<String, Object>>) pd.get("changes")).extracting(m -> m.get("field")).containsExactly("c", "font");
        long r = act(me, "A15/resetAppearance", Map.of());
        Map<String, Object> rd = (Map<String, Object>) http.getForObject("/api/v1/audit/" + r, Map.class).get("diff");
        assertThat(rd.get("from")).isEqualTo("主题色 医保青 · 正文字号 14px");

        assertThat(http.getForEntity("/api/v1/audit/999999999", Map.class).getStatusCode().value()).isEqualTo(404);
        assertThat(http.getForEntity("/api/v1/audit?type=bogus", Map.class).getStatusCode().value()).isEqualTo(400);
        assertThat(http.getForEntity("/api/v1/audit?from=yesterday", Map.class).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void exportIsCsvWithBomAndIsItselfAudited() {
        String me = uniqueActor();
        act(me, "B4/exportReport", Map.of("name", "报告,含逗号", "version", "v1"));
        // actor names flow into the 操作人 column: a leading "=" must not become a spreadsheet formula
        act("=" + me, "A3/notifyContact", Map.of("source", "结算明细", "channels", List.of("政务微信")));

        String auditor = uniqueActor();
        HttpHeaders h = new HttpHeaders();
        h.set("X-YB-User", auditor);
        ResponseEntity<byte[]> r = http.exchange("/api/v1/audit/export.csv?actor=" + me, HttpMethod.GET, new HttpEntity<>(h), byte[].class);
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getHeaders().getContentType().toString()).startsWith("text/csv");
        assertThat(r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment").contains("filename*=UTF-8''");
        byte[] b = r.getBody();
        assertThat(new byte[] {b[0], b[1], b[2]}).containsExactly(0xEF, 0xBB, 0xBF);
        String csv = new String(b, StandardCharsets.UTF_8);
        String[] lines = csv.substring(1).split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).startsWith("编号,时间,类型,操作人");
        assertThat(csv).contains("\"导出报告 · 报告,含逗号\"").contains(",'=" + me + ",").contains(",发布,").contains(",通过,");

        // the export left its own 导出 event, attributed to the requester, with the row count
        long exportId = Long.parseLong(r.getHeaders().getFirst("X-YB-Audit-Id"));
        Map<?, ?> ev = http.getForObject("/api/v1/audit/" + exportId, Map.class);
        assertThat(ev.get("actor")).isEqualTo(auditor);
        assertThat(ev.get("page")).isEqualTo("A14");
        assertThat(ev.get("action")).isEqualTo("exportAudit");
        assertThat(ev.get("type")).isEqualTo("导出");
        assertThat(((Map<?, ?>) ev.get("payload")).get("rows")).isEqualTo(2);
        assertThat(r.getHeaders().getFirst("X-YB-Watermark")).isEqualTo(ev.get("watermark"));
        assertThat(lines[1]).endsWith((String) ev.get("watermark"));
        assertThat(http.getForObject("/api/v1/audit/verify", Map.class)).containsEntry("valid", true);
    }

    @Test
    void tamperingShowsPerEventAndChainStatus() {
        String me = uniqueActor();
        long id = act(me, "A3/notifyContact", Map.of("source", "结算明细"));
        long after = act(me, "D1/markRead", Map.of("message", "n"));
        String original = jdbc.sql("select payload::text from audit_event where id = :id").param("id", id).query(String.class).single();
        try {
            jdbc.sql("update audit_event set payload = '{\"source\":\"病案首页\"}'::jsonb where id = :id").param("id", id).update();
            Map<?, ?> check = http.getForObject("/api/v1/audit/verify", Map.class);
            assertThat(check.get("valid")).isEqualTo(false);
            assertThat(((Number) check.get("brokenAt")).longValue()).isLessThanOrEqualTo(id);
            assertThat(http.getForObject("/api/v1/audit/" + id, Map.class).get("chain")).isEqualTo("broken");
            // the successor's link is intact: only the edited event is flagged
            assertThat(http.getForObject("/api/v1/audit/" + after, Map.class).get("chain")).isEqualTo("ok");
        } finally {
            jdbc.sql("update audit_event set payload = cast(:p as jsonb) where id = :id").param("p", original).param("id", id).update();
        }
        assertThat(http.getForObject("/api/v1/audit/" + id, Map.class).get("chain")).isEqualTo("ok");
    }
}
