package cn.ybdata.core.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Working-day deadlines (时限 N 个工作日, Mon–Fri) and the short time labels the screens show. */
final class Sla {
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 意见与申诉 答复时限 */
    static final int FEEDBACK_DAYS = 5;
    /** 提醒函 回执时限 */
    static final int RECEIPT_DAYS = 10;

    private static final DateTimeFormatter MD_HM = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");

    private Sla() {}

    static boolean workday(LocalDate d) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    static LocalDate addWorkingDays(LocalDate from, int n) {
        LocalDate d = from;
        int left = n;
        while (left > 0) {
            d = d.plusDays(1);
            if (workday(d)) left--;
        }
        return d;
    }

    /** signed working days from {@code today} to {@code due}: 0 = due today, negative = overdue */
    static int workingDaysLeft(LocalDate today, LocalDate due) {
        if (due.equals(today)) return 0;
        boolean ahead = due.isAfter(today);
        LocalDate a = ahead ? today : due;
        LocalDate b = ahead ? due : today;
        int n = 0;
        for (LocalDate d = a.plusDays(1); !d.isAfter(b); d = d.plusDays(1)) {
            if (workday(d)) n++;
        }
        return ahead ? n : -Math.max(n, 1);
    }

    static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    static LocalDate date(OffsetDateTime t) {
        return t.atZoneSameInstant(ZONE).toLocalDate();
    }

    static String mdhm(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ZONE).format(MD_HM);
    }

    static String md(LocalDate d) {
        return d == null ? null : d.format(MD);
    }
}
