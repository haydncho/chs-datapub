package cn.ybdata.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure rules of the A3–A7 handlers: payload validation, period keys, topic → A8 task ids. */
class WorkbenchRulesTest {

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode p(String s) throws Exception {
        return json.readTree(s);
    }

    @Test
    void textRequiresValueLengthAndAllowedCharacters() throws Exception {
        assertThat(Checks.text(p("{\"n\":\"  术前平均住院日 \"}"), "n", "名称", 30, Checks.NAME)).isEqualTo("术前平均住院日");
        assertThatThrownBy(() -> Checks.text(p("{}"), "n", "名称", 30, Checks.NAME)).hasMessage("请填写名称");
        assertThatThrownBy(() -> Checks.text(p("{\"n\":\"  \"}"), "n", "名称", 30, Checks.NAME)).hasMessage("请填写名称");
        assertThatThrownBy(() -> Checks.text(p("{\"n\":\"" + "长".repeat(31) + "\"}"), "n", "名称", 30, Checks.NAME))
                .hasMessage("名称不能超过 30 个字");
        assertThatThrownBy(() -> Checks.text(p("{\"n\":\"<script>\"}"), "n", "名称", 30, Checks.NAME)).hasMessage("名称含有不允许的特殊字符");
        assertThatThrownBy(() -> Checks.text(p("{\"n\":5}"), "n", "名称", 30, Checks.NAME)).hasMessage("名称格式不正确");
        // spreadsheet formula prefixes are refused (CSV injection), operators inside expressions are fine
        assertThatThrownBy(() -> Checks.text(p("{\"n\":\"=SUM(A1)\"}"), "n", "分子", 60, Checks.EXPR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Checks.text(p("{\"n\":\"-1+1\"}"), "n", "分子", 60, Checks.EXPR)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Checks.text(p("{\"n\":\"Σ术前住院天数\"}"), "n", "分子", 60, Checks.EXPR)).isEqualTo("Σ术前住院天数");
        assertThat(Checks.text(p("{\"n\":\"年龄 ≥ 65\"}"), "n", "条件", 30, Checks.EXPR)).isEqualTo("年龄 ≥ 65");
        assertThat(Checks.text(p("{\"n\":\"手术标志 = 1\"}"), "n", "条件", 30, Checks.EXPR)).isEqualTo("手术标志 = 1");
        assertThat(Checks.text(p("{\"n\":\"CMI值(2026版)\"}"), "n", "名称", 30, Checks.NAME)).isEqualTo("CMI值(2026版)");
    }

    @Test
    void listsOneOfIndexAndBool() throws Exception {
        assertThat(Checks.texts(p("{\"a\":[\"机构\",\"病组\"]}"), "a", "维度", 6, 4, null)).containsExactly("机构", "病组");
        assertThat(Checks.texts(p("{}"), "a", "维度", 6, 4, null)).isEmpty();
        assertThatThrownBy(() -> Checks.texts(p("{\"a\":[\"机构\",\"机构\"]}"), "a", "维度", 6, 4, null)).hasMessageContaining("重复");
        assertThatThrownBy(() -> Checks.texts(p("{\"a\":\"机构\"}"), "a", "维度", 6, 4, null)).hasMessage("维度格式不正确");
        assertThatThrownBy(() -> Checks.texts(p("{\"a\":[\"1\",\"2\",\"3\"]}"), "a", "条件", 2, 4, null)).hasMessage("条件最多 2 项");
        assertThat(Checks.oneOf(p("{\"t\":\"pct\"}"), "t", "档位", List.of("pct", "anon"))).isEqualTo("pct");
        assertThatThrownBy(() -> Checks.oneOf(p("{\"t\":\"x\"}"), "t", "档位", List.of("pct", "anon"))).hasMessageStartingWith("档位不正确");
        assertThat(Checks.index(p("{\"s\":6}"), "s", "段落", 0, 6)).isEqualTo(6);
        assertThatThrownBy(() -> Checks.index(p("{\"s\":7}"), "s", "段落", 0, 6)).hasMessage("段落不正确");
        assertThatThrownBy(() -> Checks.index(p("{\"s\":\"1\"}"), "s", "段落", 0, 6)).hasMessage("段落不正确");
        assertThat(Checks.bool(p("{}"), "b")).isFalse();
        assertThatThrownBy(() -> Checks.bool(p("{\"b\":\"yes\"}"), "b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void periodKeysAndTopicTasks() {
        assertThat(DataHubDomain.periodKey("2026年8月期")).containsExactly("202608", "2026年8月");
        assertThat(DataHubDomain.periodKey("2026年12月期")).containsExactly("202612", "2026年12月");
        assertThatThrownBy(() -> DataHubDomain.periodKey("八月")).isInstanceOf(IllegalArgumentException.class);

        assertThat(TopicDomain.codeOf("T-BR25")).isEqualTo("BR25");
        assertThat(TopicDomain.codeOf("T-GG19-drg")).isEqualTo("GG19");
        assertThat(TopicDomain.taskIdOf("T-BR25")).isEqualTo("br25");
        assertThat(TopicDomain.taskIdOf("T-GG19-drg")).isEqualTo("tp-gg19-drg");
        assertThat(TopicDomain.TOPIC_ID.matcher("T-READM14").matches()).isTrue();
        assertThat(TopicDomain.TOPIC_ID.matcher("T-GG19-drg").matches()).isTrue();
        assertThat(TopicDomain.TOPIC_ID.matcher("probe").matches()).isFalse();
        assertThat(TopicDomain.TOPIC_ID.matcher("T-x'; drop").matches()).isFalse();
        assertThat(IndicatorDomain.approvalNo(0)).matches("ZB-\\d{4}-0917");
        assertThat(IndicatorDomain.approvalNo(3)).endsWith("-0920");
    }
}
