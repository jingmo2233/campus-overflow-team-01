package com.campusoverflow.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 徽章目录的展示文案与授予阈值。
 * <p>
 * {@code BadgeType} 的 {@code displayName / description} 是前端徽章墙直接渲染的字符串，
 * 而 {@code BadgePolicy} 是纯函数——两者都属于「改一行就可能让界面出错、但不改就不会红」的部分，
 * 所以在这里用表驱动的方式把全部枚举值钉死。
 */
class BadgeCatalogTest {

    @ParameterizedTest(name = "{0} 的名称与说明都非空")
    @EnumSource(BadgeType.class)
    void everyBadgeHasDisplayNameAndDescription(BadgeType type) {
        assertThat(type.displayName()).isNotBlank();
        assertThat(type.description()).isNotBlank();
        // 枚举名形如 FIRST_ACCEPTED，直接显示会露出下划线，说明文案没配好
        assertThat(type.displayName()).doesNotContain("_");
        assertThat(type.description()).doesNotContain("_");
    }

    @Test
    void badgeCatalogCoversSixGrantScenarios() {
        assertThat(Arrays.stream(BadgeType.values()).map(BadgeType::name))
                .containsExactly("FIRST_ANSWER", "FIRST_ACCEPTED", "HELPER_10", "TEACHER_ENDORSED",
                        "REPUTATION_100", "REPUTATION_1000");
        assertThat(BadgeType.FIRST_ACCEPTED.displayName()).isEqualTo("初露锋芒");
        assertThat(BadgeType.REPUTATION_1000.description()).isEqualTo("声誉达到 1000");
    }

    @Test
    void acceptedCountAndReputationThresholdsAreCumulative() {
        // 阈值是「达到即授予」：过 10 个采纳时两个徽章同时成立，不是替换关系
        assertThat(BadgePolicy.forAcceptedCount(1)).containsExactly(BadgeType.FIRST_ACCEPTED);
        assertThat(BadgePolicy.forAcceptedCount(9)).containsExactly(BadgeType.FIRST_ACCEPTED);
        assertThat(BadgePolicy.forAcceptedCount(10))
                .containsExactlyInAnyOrder(BadgeType.FIRST_ACCEPTED, BadgeType.HELPER_10);
        assertThat(BadgePolicy.forAcceptedCount(-1)).isEmpty();

        assertThat(BadgePolicy.forReputation(99)).isEmpty();
        assertThat(BadgePolicy.forReputation(100)).containsExactly(BadgeType.REPUTATION_100);
        assertThat(BadgePolicy.forReputation(1000))
                .containsExactlyInAnyOrder(BadgeType.REPUTATION_100, BadgeType.REPUTATION_1000);
    }

    @Test
    void teacherEndorsedIsNotGrantedByThresholdPolicy() {
        // 「名师认可」由教师认证动作触发，不该被任何阈值策略顺手发出去
        assertThat(BadgePolicy.forAcceptedCount(1000)).doesNotContain(BadgeType.TEACHER_ENDORSED);
        assertThat(BadgePolicy.forReputation(99999)).doesNotContain(BadgeType.TEACHER_ENDORSED);
    }

    @Test
    void onlyTeacherGetsInitialBountyPoints() {
        // INV-R5：助教虽可管理课程，但开户积分按「教师」这一角色发放，TA 为 0——这条容易写错
        assertThat(ReputationRulebook.initialPoints(Role.TA)).isZero();
        assertThat(ReputationRulebook.initialPoints(Role.ADMIN)).isZero();
        assertThat(ReputationRulebook.initialPoints(Role.TEACHER)).isEqualTo(ReputationRulebook.TEACHER_INITIAL_POINTS);
    }

    @Test
    void badgeAwardIsAnImmutableTriple() {
        Instant at = Instant.parse("2026-10-08T02:00:00Z");
        BadgeAward award = new BadgeAward(9L, BadgeType.HELPER_10, at);

        assertThat(award.userId()).isEqualTo(9L);
        assertThat(award.type()).isEqualTo(BadgeType.HELPER_10);
        assertThat(award.awardedAt()).isEqualTo(at);
        assertThat(award).isEqualTo(new BadgeAward(9L, BadgeType.HELPER_10, at));
    }
}
