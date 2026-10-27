package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.identity.api.event.UserRegisteredEvent;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.reputation.api.event.BadgeAwardedEvent;
import com.campusoverflow.reputation.application.fake.ReputationHarness;
import com.campusoverflow.reputation.domain.BadgeAward;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.reputation.domain.Bounty;
import com.campusoverflow.reputation.domain.BountyStatus;
import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.reputation.domain.ReputationRulebook;
import com.campusoverflow.shared.security.Role;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReputationEventHandlersTest {

    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");
    private static final long AUTHOR = 100L;
    private static final long ASKER = 200L;

    private ReputationHarness harness() {
        return new ReputationHarness(NOW.toString());
    }

    private static UserRegisteredEvent registered(String eventId, long userId, Role role) {
        return new UserRegisteredEvent(eventId, NOW, userId, "用户" + userId, role);
    }

    private static AnswerAcceptedEvent accepted(String eventId, long answerAuthorId) {
        return new AnswerAcceptedEvent(eventId, NOW, 21L, 7L, answerAuthorId, ASKER, ASKER, "如何理解领域事件");
    }

    private static VoteCastEvent vote(String eventId, String targetType, long targetId, long targetAuthorId,
                                      int oldValue, int newValue) {
        return new VoteCastEvent(eventId, NOW, targetType, targetId, targetAuthorId, 300L, 7L, oldValue, newValue);
    }

    // ---------- 开户 ----------

    @Test
    @DisplayName("教师开户发放 1000 悬赏积分，学生为 0（INV-R5）")
    void registrationGrantsRoleInitialPoints() {
        ReputationHarness h = harness();

        h.handlers.on(registered("evt-r1", 9L, Role.TEACHER));
        h.handlers.on(registered("evt-r2", 100L, Role.STUDENT));

        assertThat(h.accounts.get(9L).availablePoints()).isEqualTo(ReputationRulebook.TEACHER_INITIAL_POINTS);
        assertThat(h.accounts.get(100L).availablePoints()).isZero();
        assertThat(h.accounts.get(9L).reputation()).isZero();
    }

    @Test
    @DisplayName("重复投递注册事件不会二次发放初始积分")
    void registrationIsIdempotent() {
        ReputationHarness h = harness();
        h.handlers.on(registered("evt-r1", 9L, Role.TEACHER));

        h.handlers.on(registered("evt-r1", 9L, Role.TEACHER));
        h.handlers.on(registered("evt-r9", 9L, Role.TEACHER));

        assertThat(h.accounts.get(9L).availablePoints()).isEqualTo(1000);
    }

    // ---------- 采纳 ----------

    @Test
    @DisplayName("回答被采纳：回答者 +15，写一条 ANSWER_ACCEPTED 流水")
    void acceptedCreditsAnswerAuthor() {
        ReputationHarness h = harness();

        h.handlers.on(accepted("evt-a1", AUTHOR));

        assertThat(h.accounts.get(AUTHOR).reputation()).isEqualTo(15);
        assertThat(h.ledger.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.reason()).isEqualTo(ReputationReason.ANSWER_ACCEPTED);
            assertThat(entry.delta()).isEqualTo(15);
            assertThat(entry.refType()).isEqualTo("ANSWER");
            assertThat(entry.refId()).isEqualTo(21L);
        });
    }

    @Test
    @DisplayName("自问自答被采纳不加分，也不写流水")
    void selfAcceptedGivesNothing() {
        ReputationHarness h = harness();

        h.handlers.on(accepted("evt-a1", ASKER));

        assertThat(h.accounts.findByUserId(ASKER)).isEmpty();
        assertThat(h.ledger.entries()).isEmpty();
    }

    @Test
    @DisplayName("首个回答被采纳后授予「初露锋芒」徽章")
    void acceptedGrantsFirstAcceptedBadge() {
        ReputationHarness h = harness();

        h.handlers.on(accepted("evt-a1", AUTHOR));

        assertThat(h.badges.has(AUTHOR, BadgeType.FIRST_ACCEPTED)).isTrue();
        assertThat(h.events.ofType(BadgeAwardedEvent.class)).extracting(BadgeAwardedEvent::badgeCode)
                .contains(BadgeType.FIRST_ACCEPTED.name());
    }

    @Test
    @DisplayName("累计 10 个采纳后追加「解惑达人」徽章")
    void tenthAcceptanceGrantsHelperBadge() {
        ReputationHarness h = harness();
        for (int i = 1; i <= 9; i++) {
            h.handlers.on(accepted("evt-a" + i, AUTHOR));
        }
        assertThat(h.badges.has(AUTHOR, BadgeType.HELPER_10)).isFalse();

        h.handlers.on(accepted("evt-a10", AUTHOR));

        assertThat(h.badges.has(AUTHOR, BadgeType.HELPER_10)).isTrue();
        assertThat(h.accounts.get(AUTHOR).reputation()).isEqualTo(150);
    }

    @Test
    @DisplayName("采纳事件同时结算该问题的悬赏")
    void acceptedSettlesOpenBounty() {
        ReputationHarness h = harness();
        Bounty bounty = Bounty.reconstitute(1L, 7L, 9L, 100, BountyStatus.OPEN,
                NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(2)), null, null, null);
        h.bounties.with(bounty);
        h.accounts.with(ReputationAccount.reconstitute(9L, 0, 900, 100, 0L));

        h.handlers.on(accepted("evt-a1", AUTHOR));

        assertThat(bounty.status()).isEqualTo(BountyStatus.AWARDED);
        assertThat(h.accounts.get(9L).frozenPoints()).isZero();
        // 采纳 +15 与悬赏 +100 各自入账
        assertThat(h.accounts.get(AUTHOR).reputation()).isEqualTo(115);
        assertThat(h.events.ofType(BountyAwardedEvent.class)).hasSize(1);
    }

    // ---------- 投票 ----------

    @Test
    @DisplayName("赞成 +10，反对 -2")
    void voteKeepsRulebookValues() {
        ReputationHarness h = harness();

        h.handlers.on(vote("evt-v1", "ANSWER", 21L, AUTHOR, 0, 1));
        h.handlers.on(vote("evt-v2", "QUESTION", 7L, AUTHOR, 0, -1));

        assertThat(h.accounts.get(AUTHOR).reputation()).isEqualTo(8);
    }

    @Test
    @DisplayName("改投按「新效果 − 旧效果」结算：赞改踩 −12")
    void changingVoteRecalculatesDelta() {
        ReputationHarness h = harness();
        h.handlers.on(vote("evt-v1", "ANSWER", 21L, AUTHOR, 0, 1));

        h.handlers.on(vote("evt-v2", "ANSWER", 21L, AUTHOR, 1, -1));

        assertThat(h.accounts.get(AUTHOR).reputation()).isEqualTo(-2);
        assertThat(h.ledger.entries()).extracting(LedgerEntry::delta).containsExactly(10, -12);
    }

    @Test
    @DisplayName("撤销赞成 −10；撤销反对 +2")
    void undoingVoteReversesEffect() {
        ReputationHarness h = harness();
        h.handlers.on(vote("evt-v1", "ANSWER", 21L, AUTHOR, 0, 1));
        h.handlers.on(vote("evt-v2", "ANSWER", 21L, AUTHOR, 1, 0));
        h.handlers.on(vote("evt-v3", "ANSWER", 22L, AUTHOR, 0, -1));
        h.handlers.on(vote("evt-v4", "ANSWER", 22L, AUTHOR, -1, 0));

        assertThat(h.ledger.entries()).extracting(LedgerEntry::delta).containsExactly(10, -10, -2, 2);
        assertThat(h.accounts.get(AUTHOR).reputation()).isZero();
    }

    @Test
    @DisplayName("每日上限只截断正向增量：已得 195，再获赞只入账 5")
    void dailyCapTruncatesPositiveVote() {
        ReputationHarness h = harness();
        h.ledger.append(LedgerEntry.of(AUTHOR, "seed-1", ReputationReason.ANSWER_VOTE, 195, "ANSWER", 20L,
                NOW.minus(Duration.ofHours(1))));

        h.handlers.on(vote("evt-v1", "ANSWER", 21L, AUTHOR, 0, 1));

        assertThat(h.ledger.entries()).extracting(LedgerEntry::delta).containsExactly(195, 5);
        assertThat(h.accounts.get(AUTHOR).reputation()).isEqualTo(5);
    }

    @Test
    @DisplayName("每日上限不夹负向增量：已达上限仍照常 −2")
    void dailyCapDoesNotClampNegative() {
        ReputationHarness h = harness();
        h.ledger.append(LedgerEntry.of(AUTHOR, "seed-1", ReputationReason.ANSWER_VOTE, 500, "ANSWER", 20L,
                NOW.minus(Duration.ofHours(1))));

        h.handlers.on(vote("evt-v1", "ANSWER", 21L, AUTHOR, 0, -1));

        assertThat(h.accounts.get(AUTHOR).reputation()).isEqualTo(-2);
    }

    @Test
    @DisplayName("上限的统计起点是 UTC 自然日零点：昨天的收益不占用今天额度")
    void dailyCapCountsOnlyToday() {
        ReputationHarness h = harness();
        h.ledger.append(LedgerEntry.of(AUTHOR, "seed-yesterday", ReputationReason.ANSWER_VOTE, 200, "ANSWER", 20L,
                NOW.minus(Duration.ofDays(1))));

        h.handlers.on(vote("evt-v1", "ANSWER", 21L, AUTHOR, 0, 1));

        assertThat(h.accounts.get(AUTHOR).reputation()).isEqualTo(10);
    }

    @Test
    @DisplayName("同一事件重复投递只入账一次")
    void voteIsIdempotentByEventId() {
        ReputationHarness h = harness();
        VoteCastEvent e = vote("evt-v1", "ANSWER", 21L, AUTHOR, 0, 1);

        h.handlers.on(e);
        h.handlers.on(e);

        assertThat(h.accounts.get(AUTHOR).reputation()).isEqualTo(10);
        assertThat(h.ledger.entries()).hasSize(1);
    }

    @Test
    @DisplayName("未投票到未投票的增量为 0，不写流水")
    void noopVoteWritesNothing() {
        ReputationHarness h = harness();

        h.handlers.on(vote("evt-v1", "ANSWER", 21L, AUTHOR, 0, 0));

        assertThat(h.ledger.entries()).isEmpty();
    }

    // ---------- 徽章 ----------

    @Test
    @DisplayName("提交回答只授「初来乍到」，不产生声誉流水")
    void submissionGrantsFirstAnswerBadgeOnly() {
        ReputationHarness h = harness();
        AnswerSubmittedEvent e = new AnswerSubmittedEvent("evt-s1", NOW, 21L, 7L, AUTHOR, ASKER, "如何理解领域事件");

        h.handlers.on(e);
        h.handlers.on(e);

        assertThat(h.badges.findByUser(AUTHOR)).extracting(BadgeAward::type)
                .containsExactly(BadgeType.FIRST_ANSWER);
        assertThat(h.ledger.entries()).isEmpty();
    }

    @Test
    @DisplayName("教师认证只授「名师认可」徽章，重复认证不重复发事件")
    void endorsementGrantsTeacherBadge() {
        ReputationHarness h = harness();
        AnswerEndorsedEvent e = new AnswerEndorsedEvent("evt-e1", NOW, 21L, 7L, AUTHOR, 9L, "如何理解领域事件");

        h.handlers.on(e);
        h.handlers.on(e);

        assertThat(h.badges.findByUser(AUTHOR)).extracting(BadgeAward::type)
                .containsExactly(BadgeType.TEACHER_ENDORSED);
        assertThat(h.events.ofType(BadgeAwardedEvent.class)).hasSize(1);
    }
}
