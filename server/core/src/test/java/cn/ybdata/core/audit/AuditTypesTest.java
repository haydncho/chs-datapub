package cn.ybdata.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuditTypesTest {

    private static OffsetDateTime cst(int hour, int minute) {
        return OffsetDateTime.of(2026, 10, 4, hour, minute, 0, 0, ZoneOffset.ofHours(8));
    }

    @Test
    void exactActionNamesMapToTheA14Vocabulary() {
        assertThat(AuditTypes.classify("login")).isEqualTo("登录");
        assertThat(AuditTypes.classify("selectIdentity")).isEqualTo("登录");
        assertThat(AuditTypes.classify("approvePublish")).isEqualTo("审批");
        assertThat(AuditTypes.classify("rejectPublish")).isEqualTo("审批");
        assertThat(AuditTypes.classify("signReport")).isEqualTo("审批");
        assertThat(AuditTypes.classify("setPolicyRule")).isEqualTo("配置");
        assertThat(AuditTypes.classify("publishAppearance")).isEqualTo("配置");
        assertThat(AuditTypes.classify("publishFlowVersion")).isEqualTo("配置");
        assertThat(AuditTypes.classify("exportReport")).isEqualTo("导出");
        assertThat(AuditTypes.classify("exportAudit")).isEqualTo("导出");
        assertThat(AuditTypes.classify("requestAddUser")).isEqualTo("权限");
        assertThat(AuditTypes.classify("urgeSignAll")).isEqualTo("发布");
        assertThat(AuditTypes.classify("sendReminder")).isEqualTo("发布");
        assertThat(AuditTypes.classify("markRead")).isEqualTo("查阅");
    }

    @Test
    void unknownActionsFallBackToPrefixesThenOther() {
        assertThat(AuditTypes.classify("deleteUser")).isEqualTo("删除");
        assertThat(AuditTypes.classify("removeMember")).isEqualTo("删除");
        assertThat(AuditTypes.classify("grantScope")).isEqualTo("权限");
        assertThat(AuditTypes.classify("revokeRole")).isEqualTo("权限");
        assertThat(AuditTypes.classify("viewReport")).isEqualTo("查阅");
        assertThat(AuditTypes.classify("downloadFile")).isEqualTo("导出");
        assertThat(AuditTypes.classify("approveIndicator")).isEqualTo("审批");
        assertThat(AuditTypes.classify("saveDraft")).isEqualTo("配置");
        assertThat(AuditTypes.classify("logoutAll")).isEqualTo("登录");
        assertThat(AuditTypes.classify("frobnicate")).isEqualTo("其他");
        assertThat(AuditTypes.classify(null)).isEqualTo("其他");
        assertThat(AuditTypes.kind("frobnicate").known()).isFalse();
        assertThat(AuditTypes.kind("signReport").label()).isEqualTo("签收报告");
    }

    @Test
    void everyMappedTypeIsInTheVocabulary() {
        for (String a : new String[] {"login", "approvePublish", "setPolicyRule", "exportReport", "requestAddUser",
                "urgeSign", "deleteX", "markRead", "zzz"}) {
            assertThat(AuditTypes.VOCABULARY).contains(AuditTypes.classify(a));
        }
    }

    @Test
    void sqlCaseIsGeneratedFromTheSameTable() {
        String sql = AuditTypes.sqlCase("e.action");
        assertThat(sql).startsWith("(case").endsWith("else '其他' end)")
                .contains("'approvePublish'").contains("then '审批'")
                .contains("e.action like 'delete%' then '删除'");
        // exact names are emitted before any prefix rule, so "publishAppearance" is 配置 not 发布
        assertThat(sql.indexOf("'publishAppearance'")).isLessThan(sql.indexOf("like 'publish%'"));
        assertThat(sql).doesNotContain(";");
    }

    @Test
    void offHoursIsTenPmToSixAmBeijingTime() {
        assertThat(AuditTypes.isOffHours(cst(21, 59))).isFalse();
        assertThat(AuditTypes.isOffHours(cst(22, 0))).isTrue();
        assertThat(AuditTypes.isOffHours(cst(23, 59))).isTrue();
        assertThat(AuditTypes.isOffHours(cst(0, 0))).isTrue();
        assertThat(AuditTypes.isOffHours(cst(2, 14))).isTrue();
        assertThat(AuditTypes.isOffHours(cst(5, 59))).isTrue();
        assertThat(AuditTypes.isOffHours(cst(6, 0))).isFalse();
        assertThat(AuditTypes.isOffHours(cst(12, 0))).isFalse();
        // judged in platform time, whatever offset the timestamp carries: 18:30 UTC = 02:30 CST
        assertThat(AuditTypes.isOffHours(OffsetDateTime.of(2026, 10, 3, 18, 30, 0, 0, ZoneOffset.UTC))).isTrue();
        assertThat(AuditTypes.isOffHours(OffsetDateTime.of(2026, 10, 4, 1, 0, 0, 0, ZoneOffset.UTC))).isFalse();
        assertThat(AuditTypes.sqlOffHours("e.at")).contains("Asia/Shanghai").contains(">= 22").contains("< 6");
    }

    @Test
    void riskIsOffHoursReadExportOrDelete() {
        assertThat(AuditTypes.isRisk("查阅", cst(2, 14))).isTrue();
        assertThat(AuditTypes.isRisk("导出", cst(23, 0))).isTrue();
        assertThat(AuditTypes.isRisk("删除", cst(4, 0))).isTrue();
        assertThat(AuditTypes.isRisk("配置", cst(2, 14))).isFalse();
        assertThat(AuditTypes.isRisk("查阅", cst(9, 0))).isFalse();
    }
}
