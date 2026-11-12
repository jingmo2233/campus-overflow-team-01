package com.campusoverflow.reputation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;

/**
 * 排行榜周期键。学期划分（可按学校校历调整）：2–7 月为春季学期，8–12 月与次年 1 月为秋季学期。
 */
public final class AcademicTerm {

    public static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");

    private AcademicTerm() {
    }

    /** 学期键：2–7 月为春季学期，8–12 月与次年 1 月为秋季学期（1 月归入上一年的秋季学期）。 */
    public static String termKey(Instant at) {
        LocalDate date = LocalDate.ofInstant(at, CAMPUS_ZONE);
        int month = date.getMonthValue();
        if (month >= 2 && month <= 7) {
            return date.getYear() + "-SPRING";
        }
        int fallYear = month == 1 ? date.getYear() - 1 : date.getYear();
        return fallYear + "-FALL";
    }

    /** ISO 周键，如 2026-W41。 */
    public static String weekKey(Instant at) {
        LocalDate date = LocalDate.ofInstant(at, CAMPUS_ZONE);
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return weekYear + "-W" + String.format("%02d", week);
    }

    /** 自然月键，如 2026-10。 */
    public static String monthKey(Instant at) {
        LocalDate date = LocalDate.ofInstant(at, CAMPUS_ZONE);
        return String.format("%04d-%02d", date.getYear(), date.getMonthValue());
    }
}
