package cn.ybdata.core.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

/** Pure rules of the 发布工作流 (A8) and report sign-off, kept apart from SQL so they are unit-testable. */
final class PublishRules {
    private PublishRules() {}

    static final int APPROVAL_STEP = 5;
    static final int TARGETED_RELEASE_STEP = 6;
    static final int ARCHIVED_STEP = 10;

    /** days until {@code due} (negative = overdue); null when archived or without a due date */
    static Integer daysLeft(LocalDate due, LocalDate today, int step, String status) {
        if (due == null || step >= ARCHIVED_STEP || "archived".equals(status)) return null;
        return (int) ChronoUnit.DAYS.between(today, due);
    }

    /** the queue's due text, derived from the stored due date (never a frozen demo string) */
    static String dueText(LocalDate due, LocalDate today, int step, String status) {
        if (step >= ARCHIVED_STEP || "archived".equals(status)) return "已归档";
        if ("withdrawn".equals(status)) return "已撤回";
        Integer d = daysLeft(due, today, step, status);
        if (d == null) return "未设期限";
        if (d < 0) return "逾期 " + (-d) + " 天";
        if (d == 0) return "今日到期";
        return "剩 " + d + " 天";
    }

    /**
     * Whether an A8 targeting entry (short institution name such as 第一人民医院 / 市中医院) denotes the
     * organisation {@code orgName} (示例市第一人民医院 / 示例市中医院).
     */
    static boolean sameInstitution(String orgName, String entry) {
        if (orgName == null || entry == null || orgName.isBlank() || entry.isBlank()) return false;
        String o = orgName.trim();
        String e = entry.trim();
        return o.equals(e) || (e.length() >= 4 && o.endsWith(e)) || (o.length() >= 4 && e.endsWith(o));
    }

    /** true when the organisation is one of the targeted institutions */
    static boolean covers(Collection<String> institutions, String orgName) {
        return institutions.stream().anyMatch(i -> sameInstitution(orgName, i));
    }
}
