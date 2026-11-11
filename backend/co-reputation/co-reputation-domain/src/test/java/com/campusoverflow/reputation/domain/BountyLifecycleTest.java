package com.campusoverflow.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.DomainException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 悬赏生命周期与账户积分流转的补充用例。
 * <p>
 * 原有 {@code BountyAndAccountTest} 只验证了「能不能开悬赏」「能不能只结算一次」这类主路径，
 * 每个校验分支只打中一条。这里按 {@link Bounty#open} 注释里写死的「权限 → 参数 → 问题状态」
 * 顺序逐条补齐，并覆盖 {@code award / close / isDue / assignId} 的边界。
 */
class BountyLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");
    private static final Actor TEACHER = new Actor(9, "王老师", Role.TEACHER);

    private static Bounty openBounty(int points, int days) {
        return Bounty.open(10L, true, TEACHER, true, points, days, NOW);
    }

    private static Bounty reconstituted(BountyStatus status, Instant expiresAt) {
        return Bounty.reconstitute(1L, 10L, 9L, 50, status, NOW, expiresAt, null, null, null);
    }

    @Nested
    class Open {

        @Test
        void nonManagerIsRejectedEvenWhenEveryOtherArgumentIsInvalid() {
            // 权限排在最前：参数全错也要先抛 403，否则会向无权限者泄露「积分范围」这类领域规则
            Actor student = new Actor(1, "学生", Role.STUDENT);
            assertThatThrownBy(() -> Bounty.open(10L, false, student, false, 0, 0, NOW))
                    .isInstanceOf(ForbiddenException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo("REP-3001");
        }

        @ParameterizedTest(name = "积分 {0} 超出 10–500")
        @ValueSource(ints = {9, 501})
        void pointsOutsideRangeAreRejected(int points) {
            assertThatThrownBy(() -> Bounty.open(10L, true, TEACHER, true, points, 7, NOW))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo("REP-1003");
        }

        @ParameterizedTest(name = "积分 {0} 在边界内")
        @ValueSource(ints = {Bounty.MIN_POINTS, Bounty.MAX_POINTS})
        void pointsOnBoundaryAreAccepted(int points) {
            assertThat(openBounty(points, 7).points()).isEqualTo(points);
        }

        @ParameterizedTest(name = "期限 {0} 天超出 1–14")
        @ValueSource(ints = {0, 15})
        void daysOutsideRangeAreRejected(int days) {
            // 积分合法、期限非法，才能确认命中的是 REP-1004 而不是 REP-1003
            assertThatThrownBy(() -> Bounty.open(10L, true, TEACHER, true, 50, days, NOW))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo("REP-1004");
        }

        @ParameterizedTest(name = "期限 {0} 天在边界内")
        @ValueSource(ints = {Bounty.MIN_DAYS, Bounty.MAX_DAYS})
        void daysOnBoundaryAreAccepted(int days) {
            Bounty b = openBounty(50, days);
            assertThat(b.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(days)));
        }

        @Test
        void closedQuestionIsRejectedLast() {
            // 问题状态排在最后：参数合法时才会走到这一条
            assertThatThrownBy(() -> Bounty.open(10L, false, TEACHER, true, 50, 7, NOW))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo("REP-1001");
        }

        @Test
        void newBountyStartsOpenAndUnassigned() {
            Bounty b = openBounty(50, 7);
            assertThat(b.id()).isNull();
            assertThat(b.questionId()).isEqualTo(10L);
            assertThat(b.sponsorId()).isEqualTo(TEACHER.userId());
            assertThat(b.status()).isEqualTo(BountyStatus.OPEN);
            assertThat(b.createdAt()).isEqualTo(NOW);
            assertThat(b.winnerId()).isNull();
            assertThat(b.answerId()).isNull();
            assertThat(b.closedAt()).isNull();
        }
    }

    @Nested
    class Award {

        @Test
        void awardRecordsWinnerAnswerAndClosingTime() {
            Bounty b = openBounty(50, 7);
            Instant settled = NOW.plus(Duration.ofDays(2));

            b.award(77L, 3L, settled);

            assertThat(b.status()).isEqualTo(BountyStatus.AWARDED);
            assertThat(b.answerId()).isEqualTo(77L);
            assertThat(b.winnerId()).isEqualTo(3L);
            assertThat(b.closedAt()).isEqualTo(settled);
        }

        @Test
        void sponsorCannotWinOwnBountyAndStaysOpenAfterRejection() {
            Bounty b = openBounty(50, 7);

            assertThatThrownBy(() -> b.award(77L, TEACHER.userId(), NOW))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo("REP-1005");

            // 被拒绝的结算不能留下半截状态，否则悬赏会「卡死」在既非 OPEN 也非 AWARDED 的样子
            assertThat(b.status()).isEqualTo(BountyStatus.OPEN);
            assertThat(b.winnerId()).isNull();
            assertThat(b.answerId()).isNull();
            assertThat(b.closedAt()).isNull();
        }

        @Test
        void awardedBountyCannotBeAwardedAgain() {
            Bounty b = openBounty(50, 7);
            b.award(77L, 3L, NOW);

            assertThatThrownBy(() -> b.award(78L, 4L, NOW))
                    .isInstanceOf(ConflictException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo("REP-2001");
            assertThat(b.winnerId()).isEqualTo(3L);
        }

        @Test
        void expiredBountyCannotBeAwarded() {
            Bounty b = reconstituted(BountyStatus.EXPIRED, NOW.minusSeconds(1));

            assertThatThrownBy(() -> b.award(77L, 3L, NOW))
                    .isInstanceOf(ConflictException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo("REP-2001");
        }
    }

    @Nested
    class Close {

        @Test
        void closeMarksExpiredAndStampsTime() {
            Bounty b = openBounty(50, 7);
            Instant closed = NOW.plus(Duration.ofDays(8));

            b.close(closed);

            assertThat(b.status()).isEqualTo(BountyStatus.EXPIRED);
            assertThat(b.closedAt()).isEqualTo(closed);
            assertThat(b.winnerId()).isNull();
        }

        @Test
        void closeIsIdempotentGuardNotSilent() {
            Bounty b = openBounty(50, 7);
            b.close(NOW);

            assertThatThrownBy(() -> b.close(NOW.plusSeconds(1)))
                    .isInstanceOf(ConflictException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo("REP-2001");
            assertThat(b.closedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    class DueDetection {

        @ParameterizedTest(name = "在截止时刻前 {0} 秒 → 未到期")
        @ValueSource(longs = {3600, 1})
        void beforeDeadlineIsNotDue(long secondsBefore) {
            Bounty b = openBounty(50, 1);
            assertThat(b.isDue(b.expiresAt().minusSeconds(secondsBefore))).isFalse();
        }

        @Test
        void exactlyAtDeadlineCountsAsDue() {
            // 注释写的是「等于截止时刻即视为到期」，这是最容易被写成 isAfter 的一行
            Bounty b = openBounty(50, 1);
            assertThat(b.isDue(b.expiresAt())).isTrue();
        }

        @Test
        void afterDeadlineIsDue() {
            Bounty b = openBounty(50, 1);
            assertThat(b.isDue(b.expiresAt().plusSeconds(1))).isTrue();
        }

        @ParameterizedTest(name = "状态 {0} 即使已过期也不算待处理")
        @EnumSource(value = BountyStatus.class, names = {"AWARDED", "EXPIRED"})
        void settledBountyIsNeverDue(BountyStatus status) {
            // 结算任务按 isDue 扫表，终态若返回 true 会被反复挑出来重复结算
            Bounty b = reconstituted(status, NOW.minus(Duration.ofDays(30)));
            assertThat(b.isDue(NOW)).isFalse();
        }
    }

    @Nested
    class Identity {

        @Test
        void assignIdOnlyOnce() {
            Bounty b = openBounty(50, 7);
            b.assignId(42L);

            assertThat(b.id()).isEqualTo(42L);
            assertThatThrownBy(() -> b.assignId(43L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ID 已分配");
            assertThat(b.id()).isEqualTo(42L);
        }

        @Test
        void reconstituteCarriesFullStateAndCanStillBeSettled() {
            Instant expiresAt = NOW.plus(Duration.ofDays(3));
            Bounty b = Bounty.reconstitute(5L, 10L, 9L, 80, BountyStatus.OPEN, NOW, expiresAt, null, null, null);

            assertThat(b.id()).isEqualTo(5L);
            assertThat(b.points()).isEqualTo(80);
            assertThat(b.status()).isEqualTo(BountyStatus.OPEN);
            assertThat(b.expiresAt()).isEqualTo(expiresAt);

            b.award(77L, 3L, NOW);
            assertThat(b.status()).isEqualTo(BountyStatus.AWARDED);
        }
    }

    @Nested
    class AccountPoints {

        @Test
        void zeroInitialPointsIsLegalButNegativeIsNot() {
            // 学生开户初始积分为 0（见 INV-R5），这是合法状态，不能当成「没开户」
            ReputationAccount student = ReputationAccount.open(1L, 0);
            assertThat(student.availablePoints()).isZero();
            assertThat(student.reputation()).isZero();

            assertThatThrownBy(() -> ReputationAccount.open(1L, -1))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo("REP-1006");
        }

        @Test
        void reputationOnlyMovesThroughLedgerDeltas() {
            ReputationAccount acc = ReputationAccount.open(9L, 0);
            acc.applyReputation(ReputationRulebook.UPVOTE);
            acc.applyReputation(ReputationRulebook.DOWNVOTE);

            assertThat(acc.reputation()).isEqualTo(8);
        }

        @ParameterizedTest(name = "冻结 / 支出 / 退回的数量 {0} 一律拒绝")
        @ValueSource(ints = {0, -10})
        void nonPositiveAmountsAreRejectedEverywhere(int points) {
            ReputationAccount acc = ReputationAccount.open(9L, 100);

            assertThatThrownBy(() -> acc.freezePoints(points))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo("REP-1006");
            assertThatThrownBy(() -> acc.spendFrozen(points))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo("REP-1006");
            assertThatThrownBy(() -> acc.refundFrozen(points))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo("REP-1006");

            // 负数若不拦住会逆向改余额：freezePoints(-10) 等于凭空多出 10 分可用积分
            assertThat(acc.availablePoints()).isEqualTo(100);
            assertThat(acc.frozenPoints()).isZero();
        }

        @Test
        void cannotSpendMoreThanFrozen() {
            ReputationAccount acc = ReputationAccount.open(9L, 100);
            acc.freezePoints(50);

            assertThatThrownBy(() -> acc.spendFrozen(51))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo("REP-1002");
            assertThatThrownBy(() -> acc.refundFrozen(51))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((DomainException) e).code())
                    .isEqualTo("REP-1002");
            assertThat(acc.frozenPoints()).isEqualTo(50);
        }

        @Test
        void freezeThenSpendKeepsTotalConservedAndPointsDisappear() {
            // INV-B6：结算时冻结池直接扣掉，发起人的可用 + 冻结总量应真的减少，而不是转给谁
            ReputationAccount acc = ReputationAccount.open(9L, 100);
            acc.freezePoints(50);
            acc.spendFrozen(50);

            assertThat(acc.availablePoints()).isEqualTo(50);
            assertThat(acc.frozenPoints()).isZero();
            assertThat(acc.availablePoints() + acc.frozenPoints()).isEqualTo(50);
        }

        @Test
        void refundReturnsFrozenPointsToAvailable() {
            ReputationAccount acc = ReputationAccount.open(9L, 100);
            acc.freezePoints(50);
            acc.refundFrozen(50);

            assertThat(acc.availablePoints()).isEqualTo(100);
            assertThat(acc.frozenPoints()).isZero();
        }

        @Test
        void reconstituteRestoresPersistedState() {
            ReputationAccount acc = ReputationAccount.reconstitute(9L, 320, 700, 300, 4L);

            assertThat(acc.userId()).isEqualTo(9L);
            assertThat(acc.reputation()).isEqualTo(320);
            assertThat(acc.availablePoints()).isEqualTo(700);
            assertThat(acc.frozenPoints()).isEqualTo(300);
            assertThat(acc.version()).isEqualTo(4L);
        }
    }

    @Nested
    class Ledger {

        @Test
        void ofBuildsEntryWithoutIdSoRepositoryMayAssignIt() {
            LedgerEntry entry = LedgerEntry.of(9L, "evt-1", ReputationReason.ANSWER_ACCEPTED, 15, "ANSWER", 77L, NOW);

            assertThat(entry.id()).isNull();
            assertThat(entry.userId()).isEqualTo(9L);
            assertThat(entry.eventId()).isEqualTo("evt-1");
            assertThat(entry.reason()).isEqualTo(ReputationReason.ANSWER_ACCEPTED);
            assertThat(entry.delta()).isEqualTo(15);
            assertThat(entry.refType()).isEqualTo("ANSWER");
            assertThat(entry.refId()).isEqualTo(77L);
            assertThat(entry.createdAt()).isEqualTo(NOW);
        }

        @Test
        void eventIdAndReasonAreMandatory() {
            // 两者构成 (eventId, userId, reason) 幂等键，为空会让重复投递失去唯一约束
            assertThatThrownBy(() -> new LedgerEntry(1L, 9L, null, ReputationReason.QUESTION_VOTE, 10, "QUESTION", 1L,
                    NOW)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new LedgerEntry(1L, 9L, "evt-1", null, 10, "QUESTION", 1L, NOW))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void distinctEntriesWithSameContentAreNotEqualBecauseIdDiffers() {
            LedgerEntry a = new LedgerEntry(1L, 9L, "evt-1", ReputationReason.QUESTION_VOTE, 10, "QUESTION", 1L, NOW);
            LedgerEntry b = new LedgerEntry(2L, 9L, "evt-1", ReputationReason.QUESTION_VOTE, 10, "QUESTION", 1L, NOW);

            assertThat(a).isNotEqualTo(b).isNotEqualTo(1L);
            assertThat(a).isEqualTo(new LedgerEntry(1L, 9L, "evt-1", ReputationReason.QUESTION_VOTE, 10, "QUESTION",
                    1L, NOW)).hasSameHashCodeAs(new LedgerEntry(1L, 9L, "evt-1", ReputationReason.QUESTION_VOTE, 10,
                    "QUESTION", 1L, NOW));
            assertThat(a.toString()).contains("evt-1");
        }
    }

    @Nested
    class Reasons {

        @ParameterizedTest(name = "{0} 的中文标签是 {1}")
        @CsvSource({
                "ANSWER_ACCEPTED, 回答被采纳",
                "QUESTION_VOTE, 问题被投票",
                "ANSWER_VOTE, 回答被投票",
                "BOUNTY_AWARDED, 赢得悬赏"
        })
        void everyReasonHasChineseLabel(ReputationReason reason, String label) {
            assertThat(reason.label()).isEqualTo(label);
            // 前端错误提示直接展示 label，出现英文枚举名说明有分支漏了中文
            assertThat(reason.label()).doesNotContain("_");
        }

        @ParameterizedTest(name = "{0} 是否属于投票类 = {1}")
        @CsvSource({
                "QUESTION_VOTE, true",
                "ANSWER_VOTE, true",
                "ANSWER_ACCEPTED, false",
                "BOUNTY_AWARDED, false"
        })
        void onlyVoteReasonsCountTowardDailyCap(ReputationReason reason, boolean vote) {
            assertThat(reason.isVote()).isEqualTo(vote);
        }
    }
}
