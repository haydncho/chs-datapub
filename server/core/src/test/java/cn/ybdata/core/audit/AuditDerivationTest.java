package cn.ybdata.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Display / diff / CSV helpers that do not need a database. */
class AuditDerivationTest {

    private final ObjectMapper om = new ObjectMapper();

    private JsonNode j(String s) throws Exception {
        return om.readTree(s);
    }

    @Test
    void a13DiffUsesPreviousValueOrDefault() throws Exception {
        var first = SettingDiff.compute("A13", "setPolicyRule", j("{\"key\":\"minOrg\",\"value\":6}"), null);
        assertThat(first.changes()).singleElement().satisfies(c -> {
            assertThat(c.from()).isEqualTo("5 家");
            assertThat(c.to()).isEqualTo("6 家");
        });
        assertThat(first.from()).isEqualTo("小样本抑制 · 同级机构数 5 家");
        var second = SettingDiff.compute("A13", "setPolicyRule", j("{\"key\":\"wm\",\"value\":false}"),
                j("{\"key\":\"wm\",\"value\":true}"));
        assertThat(second.from()).isEqualTo("动态水印 开启");
        assertThat(second.to()).isEqualTo("动态水印 关闭");
    }

    @Test
    void a15DiffListsOnlyChangedFields() throws Exception {
        var d = SettingDiff.compute("A15", "publishAppearance",
                j("{\"c\":1,\"dens\":1,\"rad\":2,\"font\":1,\"card\":1,\"scr\":0,\"mot\":1,\"rot\":20,\"wm\":1,\"name\":\"医保数据公开 · 定向发布平台\"}"), null);
        assertThat(d.changes()).extracting(SettingDiff.Change::field).containsExactly("c", "rad");
        assertThat(d.from()).isEqualTo("主题色 政务蓝 · 圆角 标准 12");
        assertThat(d.to()).isEqualTo("主题色 医保青 · 圆角 圆润 18");
        // reset goes back to the default from whatever was published before
        var reset = SettingDiff.compute("A15", "resetAppearance", j("{}"), j("{\"c\":3}"));
        assertThat(reset.changes()).singleElement().satisfies(c -> {
            assertThat(c.from()).isEqualTo("中国红");
            assertThat(c.to()).isEqualTo("政务蓝");
        });
        assertThat(SettingDiff.applies("A15", "resetAppearance")).isTrue();
        assertThat(SettingDiff.applies("A8", "approvePublish")).isFalse();
    }

    @Test
    void displayTimeTodayYesterdayOlder() {
        var now = OffsetDateTime.of(2026, 10, 4, 10, 0, 0, 0, ZoneOffset.ofHours(8));
        assertThat(AuditService.displayTime(now.minusHours(1), now)).isEqualTo("09:00:00");
        assertThat(AuditService.displayTime(OffsetDateTime.of(2026, 10, 3, 17, 40, 0, 0, ZoneOffset.ofHours(8)), now))
                .isEqualTo("昨天 17:40");
        assertThat(AuditService.displayTime(OffsetDateTime.of(2026, 9, 28, 8, 5, 0, 0, ZoneOffset.ofHours(8)), now))
                .isEqualTo("09-28 08:05");
    }

    @Test
    void watermarkTerminalAndObject() throws Exception {
        assertThat(AuditService.watermark("7f3a11c2" + "0".repeat(56))).isEqualTo("WM-7F3A-11C2");
        assertThat(AuditService.splitTerminal("10.86.31.12 · Mozilla/5.0 (X11) AppleWebKit Chrome/128.0 Safari/537"))
                .containsExactly("10.86.31.12", "Chrome 128");
        assertThat(AuditService.browser("Mozilla/5.0 Chrome/128.0 Safari/537.36 Edg/128.0")).isEqualTo("Edge 128");
        assertThat(AuditService.splitTerminal(null)).containsExactly("—", "—");
        assertThat(AuditService.describe("D1", AuditTypes.kind("signReport"), j("{\"report\":\"2026年8月 本院医保运行报告\"}")))
                .isEqualTo("签收报告 · 2026年8月 本院医保运行报告");
        assertThat(AuditService.describe("A13", AuditTypes.kind("setPolicyRule"), j("{\"key\":\"minCase\",\"value\":35}")))
                .isEqualTo("展示策略 · 小样本抑制 · 病组病例数");
        assertThat(AuditService.describe("A3", AuditTypes.kind("frobnicate"), j("{}"))).isEqualTo("数据归集中心 · frobnicate");
        assertThat(AuditService.describe("A14", AuditTypes.kind("exportAudit"), j("{\"rows\":18,\"filters\":{}}")))
                .isEqualTo("导出审计日志 · 18 条");
        assertThat(AuditService.describe("A15", AuditTypes.kind("publishAppearance"), j("{\"c\":1,\"name\":\"x\"}")))
                .isEqualTo("外观配置发布");
    }

    @Test
    void csvCellsAreQuotedAndFormulaSafe() {
        assertThat(AuditCsv.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(AuditCsv.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(AuditCsv.cell("=HYPERLINK(1)")).isEqualTo("'=HYPERLINK(1)");
        assertThat(AuditCsv.cell("@x")).isEqualTo("'@x");
        assertThat(AuditCsv.cell(null)).isEmpty();
        byte[] csv = AuditCsv.render(java.util.List.of(), "WM-0000-0000");
        assertThat(csv[0]).isEqualTo((byte) 0xEF);
        assertThat(csv[1]).isEqualTo((byte) 0xBB);
        assertThat(csv[2]).isEqualTo((byte) 0xBF);
        assertThat(new String(csv, StandardCharsets.UTF_8)).startsWith("﻿编号,时间,类型");
    }

    @Test
    void timeParamsAcceptDatesAndDateTimes() {
        assertThat(AuditController.parseTime("2026-10-04", false)).isEqualTo(OffsetDateTime.parse("2026-10-04T00:00+08:00"));
        assertThat(AuditController.parseTime("2026-10-04", true)).isEqualTo(OffsetDateTime.parse("2026-10-05T00:00+08:00"));
        assertThat(AuditController.parseTime("2026-10-04T08:30:00", false)).isEqualTo(OffsetDateTime.parse("2026-10-04T08:30+08:00"));
        assertThat(AuditController.parseTime("2026-10-04T00:30:00Z", false)).isEqualTo(OffsetDateTime.parse("2026-10-04T00:30Z"));
        assertThat(AuditController.parseTime(" ", false)).isNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AuditController.parseTime("yesterday", false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
