package cn.ybdata.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublishRulesTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    @Test
    void dueTextFollowsTheStoredDueDate() {
        assertThat(PublishRules.dueText(LocalDate.of(2026, 10, 5), TODAY, 5, "open")).isEqualTo("逾期 2 天");
        assertThat(PublishRules.dueText(TODAY, TODAY, 7, "open")).isEqualTo("今日到期");
        assertThat(PublishRules.dueText(LocalDate.of(2026, 10, 8), TODAY, 3, "open")).isEqualTo("剩 1 天");
        assertThat(PublishRules.dueText(LocalDate.of(2026, 10, 22), TODAY, 2, "open")).isEqualTo("剩 15 天");
        assertThat(PublishRules.dueText(null, TODAY, 10, "archived")).isEqualTo("已归档");
        assertThat(PublishRules.dueText(LocalDate.of(2026, 10, 1), TODAY, 10, "open")).isEqualTo("已归档");
        assertThat(PublishRules.dueText(null, TODAY, 1, "open")).isEqualTo("未设期限");
        assertThat(PublishRules.daysLeft(LocalDate.of(2026, 10, 5), TODAY, 5, "open")).isEqualTo(-2);
        assertThat(PublishRules.daysLeft(null, TODAY, 5, "open")).isNull();
    }

    @Test
    void institutionEntriesMatchTheirOrganisation() {
        assertThat(PublishRules.sameInstitution("示例市第一人民医院", "第一人民医院")).isTrue();
        assertThat(PublishRules.sameInstitution("示例市中医院", "市中医院")).isTrue();
        assertThat(PublishRules.sameInstitution("甲县人民医院", "甲县人民医院")).isTrue();
        assertThat(PublishRules.sameInstitution("示例市第二人民医院", "第一人民医院")).isFalse();
        assertThat(PublishRules.sameInstitution("示例市第一人民医院", "")).isFalse();
        assertThat(PublishRules.covers(List.of("第二人民医院", "市中医院"), "示例市第一人民医院")).isFalse();
        assertThat(PublishRules.covers(List.of("第一人民医院"), "示例市第一人民医院")).isTrue();
    }

    // ── A9 flow validation ───────────────────────────────────────────────

    static final Set<String> LANES = Set.of("行政管理组", "委托分析团队", "专家组", "召集人", "定点医疗机构");
    static final Set<String> TIMEOUTS = Set.of("提醒承办人", "自动催办", "升级至召集人", "视为通过");
    static final Set<String> CHANNELS = Set.of("站内信", "政务微信", "短信");

    static JsonNode nodes(String jsonArray) throws Exception {
        return new ObjectMapper().readTree(jsonArray.replace('\'', '"'));
    }

    static final String MONTHLY = """
            [{'lane':'行政管理组','col':0,'name':'生成草稿','kind':'自动','days':1},
             {'lane':'行政管理组','col':1,'name':'数据复核','kind':'人工','days':2},
             {'lane':'召集人','col':2,'name':'审批','kind':'审批','days':2},
             {'lane':'定点医疗机构','col':3,'name':'签收','kind':'签收','days':3},
             {'lane':'定点医疗机构','col':4,'name':'意见','kind':'可选','days':10},
             {'lane':'行政管理组','col':5,'name':'答复闭环','kind':'人工','days':5}]""";

    @Test
    void seededMonthlyFlowIsValid() throws Exception {
        assertThat(FlowRules.criticalPath(nodes(MONTHLY))).isEqualTo(13);
        assertThat(FlowRules.problems(nodes(MONTHLY), LANES, TIMEOUTS, CHANNELS, 15)).isEmpty();
    }

    @Test
    void approvalNodeMustBeHandledByTheConvener() throws Exception {
        JsonNode n = nodes(MONTHLY.replace("'lane':'召集人'", "'lane':'定点医疗机构'"));
        assertThat(FlowRules.problems(n, LANES, TIMEOUTS, CHANNELS, 15))
                .anyMatch(p -> p.contains("召集人承办的审批节点"))
                .anyMatch(p -> p.contains("审批节点「审批」须由召集人承办"));
    }

    @Test
    void criticalPathOverTheLegalLimitAndGapsAreRefused() throws Exception {
        JsonNode slow = nodes(MONTHLY.replace("'days':5}", "'days':9}"));
        assertThat(FlowRules.problems(slow, LANES, TIMEOUTS, CHANNELS, 15)).anyMatch(p -> p.contains("超过法定期限"));
        JsonNode gap = nodes(MONTHLY.replace("'col':1,", "'col':2,").replace("'col':0,", "'col':1,"));
        assertThat(FlowRules.problems(gap, LANES, TIMEOUTS, CHANNELS, 15)).anyMatch(p -> p.contains("起止节点不完整"));
        JsonNode bad = nodes("[{'lane':'外星人','col':0,'name':'','kind':'魔法','days':-1,'channels':['飞鸽']}]");
        assertThat(FlowRules.problems(bad, LANES, TIMEOUTS, CHANNELS, 15)).hasSizeGreaterThanOrEqualTo(5);
        assertThat(FlowRules.problems(nodes("[]"), LANES, TIMEOUTS, CHANNELS, 15)).containsExactly("流程至少需要 1 个节点");
    }
}
