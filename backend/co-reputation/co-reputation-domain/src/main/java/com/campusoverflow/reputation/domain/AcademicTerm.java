package com.campusoverflow.reputation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.Locale;

/**
 * 排行榜周期键。学期划分（可按学校校历调整）：2–7 月为春季学期，8–12 月与次年 1 月为秋季学期。
 */
public final class AcademicTerm {

    public static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");

    private AcademicTerm() {
    }

    /** 形如 2026-FALL。1 月归属上一个自然年的秋季学期，故先减一年再拼键。 */
    public static String termKey(Instant at) {
        LocalDate date = at.atZone(CAMPUS_ZONE).toLocalDate();
        int month = date.getMonthValue();
        int year = date.getYear();
        if (month == 1) {
            return (year - 1) + "-FALL";
        }
        return year + (month <= 7 ? "-SPRING" : "-FALL");
    }

    /** ISO-8601 周键，形如 2026-W41；跨年周按 ISO 周年而非自然年命名。 */
    public static String weekKey(Instant at) {
        LocalDate date = at.atZone(CAMPUS_ZONE).toLocalDate();
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format(Locale.ROOT, "%04d-W%02d", weekYear, week);
    }

    /** 形如 2026-10。 */
    public static String monthKey(Instant at) {
        LocalDate date = at.atZone(CAMPUS_ZONE).toLocalDate();
        return String.format(Locale.ROOT, "%04d-%02d", date.getYear(), date.getMonthValue());
    }
}
