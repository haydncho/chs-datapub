package cn.ybdata.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeedbackAlertRulesTest {

    private static final ObjectMapper M = new ObjectMapper();

    @Test
    void workingDaysSkipWeekends() {
        // 2026-10-02 is a Friday
        LocalDate fri = LocalDate.of(2026, 10, 2);
        assertThat(Sla.addWorkingDays(fri, 1)).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(Sla.addWorkingDays(fri, 5)).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(Sla.workingDaysLeft(fri, fri)).isZero();
        assertThat(Sla.workingDaysLeft(fri, LocalDate.of(2026, 10, 9))).isEqualTo(5);
        assertThat(Sla.workingDaysLeft(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 5))).isEqualTo(-2);
        // overdue over a weekend is still overdue
        assertThat(Sla.workingDaysLeft(LocalDate.of(2026, 10, 3), fri)).isEqualTo(-1);
    }

    @Test
    void overdueIsDerivedFromSla() {
        OffsetDateTime created = OffsetDateTime.parse("2026-09-28T10:12:00+08:00");
        FeedbackDomain.Row open = new FeedbackDomain.Row("YJ-1", "意见", "H001", "x", "t", null, null, "doing", "王倩", "b",
                created, null, null, null, "[]", null, null, null, null, false);
        assertThat(open.due()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(open.shownStatus(LocalDate.of(2026, 10, 2))).isEqualTo("doing");
        assertThat(open.shownStatus(LocalDate.of(2026, 10, 7))).isEqualTo("over");
        FeedbackDomain.Row done = new FeedbackDomain.Row("YJ-2", "意见", "H001", "x", "t", null, null, "done", "王倩", "b",
                created, null, null, null, "[]", null, null, null, null, false);
        assertThat(done.shownStatus(LocalDate.of(2026, 10, 7))).isEqualTo("done");
    }

    @Test
    void requiredFieldsAreTrimmedAndBounded() throws Exception {
        var p = M.readTree("{\"text\":\"  答复  \",\"blank\":\"   \",\"obj\":{}}");
        assertThat(FeedbackDomain.required(p, "text", "答复内容", 10)).isEqualTo("答复");
        assertThatThrownBy(() -> FeedbackDomain.required(p, "blank", "答复内容", 10)).hasMessage("请填写答复内容");
        assertThatThrownBy(() -> FeedbackDomain.required(p, "missing", "承办人", 10)).hasMessage("请填写承办人");
        assertThatThrownBy(() -> FeedbackDomain.required(p, "obj", "承办人", 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FeedbackDomain.required(p, "text", "答复内容", 1)).hasMessageContaining("不能超过");
    }

    @Test
    void receiptTitleMatchesAlertByMetric() {
        AlertDomain.Alert iu29 = new AlertDomain.Alert("AL-07", "H001", "示例市第一人民医院", "IU29 例均基金差额", "+1,620",
                "> +1,500 元", "low", "sent", null, null);
        AlertDomain.Alert oop = new AlertDomain.Alert("AL-05", "H020", "丙区第2医院", "医保外费用占比", "11.2%",
                "> P90", "mid", "unsent", null, null);
        assertThat(AlertDomain.matches(iu29, "IU29 例均基金差额超阈值", null)).isTrue();
        assertThat(AlertDomain.matches(oop, "医保外费用占比 超阈值", null)).isTrue();
        assertThat(AlertDomain.matches(oop, "IU29 例均基金差额超阈值", null)).isFalse();
        // a free-text first word is not a DRG code
        assertThat(AlertDomain.matches(iu29, "例均 x", null)).isFalse();
        assertThat(AlertDomain.matches(iu29, "anything", "AL-07")).isTrue();
        assertThat(AlertDomain.matches(iu29, "IU29 例均基金差额超阈值", "AL-06")).isFalse();
    }

    @Test
    void percentileMatchesAnalyticsService() {
        // ES35 peer group of the demo data → P90 = 6.2
        assertThat(AlertRules.percentile(List.of(4.1, 4.5, 4.8, 5.0, 5.3, 5.6, 6.2, 6.2), 90)).isEqualTo(6.2, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(AlertRules.percentile(List.of(1.0, 2.0), 50)).isEqualTo(1.5);
    }
}
