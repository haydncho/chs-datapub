package cn.ybdata.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ybdata.core.security.AccessPolicy;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserMatrixTest {

    private static UserMatrix.Cell cell(String role, String groupId) {
        return UserMatrix.row(role).stream().filter(c -> c.group().equals(groupId)).findFirst().orElseThrow();
    }

    @Test
    void groupsPartitionEveryPageExceptLogin() {
        List<String> grouped = UserMatrix.GROUPS.stream().flatMap(g -> g.pages().stream()).toList();
        assertThat(grouped).doesNotHaveDuplicates().doesNotContain("A1");
        assertThat(grouped).containsExactlyInAnyOrderElementsOf(AccessPolicy.ALL_PAGES.stream().filter(p -> !p.equals("A1")).toList());
        assertThat(UserMatrix.PAGE_NAMES.keySet()).containsExactlyInAnyOrderElementsOf(grouped);
    }

    @Test
    void convenerReachesEveryGroup() {
        assertThat(UserMatrix.row("convener")).allSatisfy(c -> {
            assertThat(c.level()).isEqualTo("access");
            assertThat(c.pages()).hasSize(c.total());
        });
    }

    @Test
    void adminHasNoOrgSideFunctions() {
        assertThat(cell("admin", "org").level()).isEqualTo("none");
        assertThat(cell("admin", "s4").pages()).containsExactly("A8", "A9");
        assertThat(cell("admin", "cock").level()).isEqualTo("access");
    }

    @Test
    void auditorIsReadOnlyOnlyWhereItCanOpenSomething() {
        UserMatrix.Cell gov = cell("auditor", "gov");
        assertThat(gov.level()).isEqualTo("readonly");
        assertThat(gov.pages()).containsExactly("A14");
        assertThat(gov.total()).isEqualTo(3);
        assertThat(cell("auditor", "s2").level()).isEqualTo("none");
    }

    @Test
    void partialAccessKeepsOnlyOpenPages() {
        assertThat(cell("analyst", "s3").pages()).containsExactly("A6", "A7");
        assertThat(cell("county", "s5").pages()).containsExactly("A11");
        assertThat(cell("hospital", "org").pages()).containsExactly("B1", "B2", "B3", "B4", "B5", "B6", "B7", "D1");
        assertThat(cell("observer", "org").pages()).containsExactly("C3");
        assertThat(UserMatrix.row("nobody")).allSatisfy(c -> assertThat(c.level()).isEqualTo("none"));
    }

    @Test
    void sidesAndApproval() {
        for (String r : List.of("convener", "admin", "analyst", "auditor")) assertThat(UserMatrix.sideOf(r)).isEqualTo("bureau");
        for (String r : List.of("hospital", "county", "observer")) assertThat(UserMatrix.sideOf(r)).isEqualTo("org");
        assertThat(UserMatrix.canApprove("convener")).isTrue();
        assertThat(UserMatrix.canApprove("admin")).isFalse();
    }

    @Test
    void statusRules() {
        Instant now = Instant.parse("2026-10-05T04:00:00Z");
        assertThat(UserMatrix.statusOf(true, now.minus(2, ChronoUnit.HOURS), now)).isEqualTo("on");
        assertThat(UserMatrix.statusOf(true, now.minus(30, ChronoUnit.DAYS), now)).isEqualTo("on");
        assertThat(UserMatrix.statusOf(true, now.minus(31, ChronoUnit.DAYS), now)).isEqualTo("expiring");
        assertThat(UserMatrix.statusOf(true, null, now)).isEqualTo("on");
        assertThat(UserMatrix.statusOf(false, now, now)).isEqualTo("off");
        assertThat(UserMatrix.statusOf(false, now.minus(90, ChronoUnit.DAYS), now)).isEqualTo("off");
    }

    @Test
    void lastLoginText() {
        ZoneId z = ZoneId.of("Asia/Shanghai");
        Instant now = Instant.parse("2026-10-05T04:00:00Z"); // 12:00 本地
        assertThat(UserMatrix.lastLoginText(null, now, z)).isEqualTo("从未登录");
        assertThat(UserMatrix.lastLoginText(Instant.parse("2026-10-05T01:02:00Z"), now, z)).isEqualTo("今天 09:02");
        assertThat(UserMatrix.lastLoginText(Instant.parse("2026-10-04T00:47:00Z"), now, z)).isEqualTo("昨天 08:47");
        assertThat(UserMatrix.lastLoginText(Instant.parse("2026-10-02T04:00:00Z"), now, z)).isEqualTo("3 天前");
        assertThat(UserMatrix.lastLoginText(Instant.parse("2026-08-26T04:00:00Z"), now, z)).isEqualTo("08-26");
    }

    @Test
    void actionPermissions() {
        for (String a : List.of("setUserEnabled", "requestAddUser")) {
            assertThat(UserMatrix.mayAct("convener", a)).as(a).isTrue();
            for (String r : List.of("admin", "analyst", "hospital", "county", "auditor", "observer")) {
                assertThat(UserMatrix.mayAct(r, a)).as(r + "/" + a).isFalse();
            }
        }
        assertThat(UserMatrix.mayAct("convener", "reviewAddUser")).isTrue();
        assertThat(UserMatrix.mayAct("admin", "reviewAddUser")).isTrue();
        assertThat(UserMatrix.mayAct("analyst", "reviewAddUser")).isFalse();
        assertThat(UserMatrix.mayAct("convener", "unknown")).isFalse();
    }

    @Test
    void roleMustMatchTheKindOfOrg() {
        for (String r : List.of("convener", "admin", "analyst", "auditor")) {
            assertThat(UserMatrix.orgFits(r, "医保局")).as(r).isTrue();
            assertThat(UserMatrix.orgFits(r, "市三级")).as(r).isFalse();
        }
        assertThat(UserMatrix.orgFits("observer", "公开")).isTrue();
        assertThat(UserMatrix.orgFits("observer", "医保局")).isFalse();
        assertThat(UserMatrix.orgFits("hospital", "市三级")).isTrue();
        assertThat(UserMatrix.orgFits("hospital", "医保局")).isFalse();
        assertThat(UserMatrix.orgFits("county", "县三级")).isTrue();
        assertThat(UserMatrix.orgFits("county", "医保局")).isFalse();
        assertThat(UserMatrix.orgFits("county", null)).isFalse();
    }
}
