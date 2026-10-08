package cn.ybdata.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** A13 / A15 server-side validation (mirrors web/src/mock/A13.ts and appearance.ts normalizeAppearance). */
class SettingValidationTest {

    private static final ObjectMapper M = new ObjectMapper();
    private final SettingDomain domain = new SettingDomain(null, M);

    private static JsonNode j(String s) {
        try {
            return M.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void a13Rules() {
        SettingDomain.validRule("minOrg", j("6"));
        SettingDomain.validRule("minCase", j("35"));
        SettingDomain.validRule("wm", j("false"));
        for (String bad : new String[] {"-999", "1", "11", "6.5", "\"abc\"", "null", "{\"x\":1}"}) {
            assertThatThrownBy(() -> SettingDomain.validRule("minOrg", j(bad))).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> SettingDomain.validRule("minCase", j("33"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettingDomain.validRule("wm", j("1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettingDomain.validRule("bogus", j("1"))).isInstanceOf(IllegalArgumentException.class);
    }

    private static final String OK = "{\"c\":0,\"dens\":1,\"rad\":1,\"font\":1,\"card\":1,\"scr\":0,\"mot\":1,\"rot\":20,\"wm\":1,"
            + "\"name\":\"医保数据公开 · 定向发布平台\"";

    @Test
    void a15Appearance() {
        assertThat(domain.validAppearance(j(OK + "}")).get("rot").asInt()).isEqualTo(20);
        assertThat(domain.validAppearance(j(OK + ",\"custom\":\"#1e5bd8\",\"menu\":1,\"zebra\":0}")).get("custom").asText()).isEqualTo("#1E5BD8");
        for (String bad : new String[] {
                OK.replace("\"c\":0", "\"c\":9") + "}",
                OK.replace("\"rot\":20", "\"rot\":2") + "}",
                OK.replace("\"rot\":20", "\"rot\":20.5") + "}",
                OK.replace("\"dens\":1", "\"dens\":\"1\"") + "}",
                OK.replace("医保数据公开 · 定向发布平台", "x".repeat(41)) + "}",
                OK.replace("医保数据公开 · 定向发布平台", " ") + "}",
                OK + ",\"custom\":\"#FFFF00\"}", // too light for white text
                OK + ",\"custom\":\"red\"}",
                OK + ",\"evil\":1}",
                "{\"c\":0}"}) {
            assertThatThrownBy(() -> domain.validAppearance(j(bad))).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
