package com.campusoverflow.reputation.domain;

import java.util.EnumSet;
import java.util.Set;

/** 徽章授予规则（纯函数，易于测试与扩展）。 */
public final class BadgePolicy {

    private BadgePolicy() {
    }

    /** 按累计被采纳数授予：首个采纳得「初露锋芒」，满 10 个再得「解惑达人」。 */
    public static Set<BadgeType> forAcceptedCount(long acceptedCount) {
        Set<BadgeType> badges = EnumSet.noneOf(BadgeType.class);
        if (acceptedCount >= 1) {
            badges.add(BadgeType.FIRST_ACCEPTED);
        }
        if (acceptedCount >= 10) {
            badges.add(BadgeType.HELPER_10);
        }
        return badges;
    }

    /** 按当前声誉值授予：满 100 得「小有名气」，满 1000 再得「德高望重」。 */
    public static Set<BadgeType> forReputation(int reputation) {
        Set<BadgeType> badges = EnumSet.noneOf(BadgeType.class);
        if (reputation >= 100) {
            badges.add(BadgeType.REPUTATION_100);
        }
        if (reputation >= 1000) {
            badges.add(BadgeType.REPUTATION_1000);
        }
        return badges;
    }
}
