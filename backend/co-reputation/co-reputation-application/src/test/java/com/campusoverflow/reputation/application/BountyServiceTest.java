package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyClosedEvent;
import com.campusoverflow.reputation.api.event.BountyOpenedEvent;
import com.campusoverflow.reputation.application.fake.ReputationHarness;
import com.campusoverflow.reputation.domain.Bounty;
import com.campusoverflow.reputation.domain.BountyStatus;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.reputation.domain.ReputationRulebook;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BountyServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");
    private static final Actor TEACHER = new Actor(9L, "王老师", Role.TEACHER);
    private static final Actor STUDENT = new Actor(100L, "李四", Role.STUDENT);

    private ReputationHarness harness() {
        ReputationHarness h = new ReputationHarness(NOW.toString());
        h.identity.withUser(9L, "王老师", Role.TEACHER)
                .withUser(100L, "李四", Role.STUDENT)
                .withCourseManager(9L);
        h.qa.withQuestion(7L, 100L, 5L, null, false);
        h.accounts.with(ReputationAccount.open(9L, ReputationRulebook.TEACHER_INITIAL_POINTS))
                .with(ReputationAccount.open(100L, 0));
        return h;
    }

    @Test
    @DisplayName("发起悬赏：积分从可用转入冻结，总量不变，并发出 BountyOpened")
    void openFreezesPoints() {
        ReputationHarness h = harness();

        long id = h.bountyService.open(TEACHER, 7L, 100, 3);

        ReputationAccount sponsor = h.accounts.get(9L);
        assertThat(id).isPositive();
        assertThat(sponsor.availablePoints()).isEqualTo(900);
        assertThat(sponsor.frozenPoints()).isEqualTo(100);
        assertThat(sponsor.availablePoints() + sponsor.frozenPoints()).isEqualTo(1000);
        assertThat(h.events.ofType(BountyOpenedEvent.class)).hasSize(1);
        assertThat(h.audit.actions).containsExactly("BOUNTY_OPENED");
    }

    @Test
    @DisplayName("非课程管理者不能发起悬赏（403 REP-3001），积分不被冻结")
    void openRequiresCourseManager() {
        ReputationHarness h = harness();

        assertThatThrownBy(() -> h.bountyService.open(STUDENT, 7L, 100, 3))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        ex -> assertThat(ex.code()).isEqualTo("REP-3001"));
        assertThat(h.accounts.get(100L).frozenPoints()).isZero();
        assertThat(h.events.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("同一问题已有进行中的悬赏时拒绝（409 REP-2002）")
    void openRejectsSecondOpenBounty() {
        ReputationHarness h = harness();
        h.bountyService.open(TEACHER, 7L, 100, 3);

        assertThatThrownBy(() -> h.bountyService.open(TEACHER, 7L, 50, 3))
                .isInstanceOfSatisfying(ConflictException.class,
                        ex -> assertThat(ex.code()).isEqualTo("REP-2002"));
        assertThat(h.accounts.get(9L).frozenPoints()).isEqualTo(100);
    }

    @Test
    @DisplayName("可用积分不足时整个用例回滚，不留下悬赏记录")
    void openRejectsInsufficientPoints() {
        ReputationHarness h = harness();
        h.accounts.save(ReputationAccount.reconstitute(9L, 0, 50, 0, 0L));

        assertThatThrownBy(() -> h.bountyService.open(TEACHER, 7L, 100, 3))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.code()).isEqualTo("REP-1002"));
        assertThat(h.bounties.findLatestByQuestion(7L)).isEmpty();
    }

    @Test
    @DisplayName("已删除或已采纳的问题不能发起悬赏（REP-1001）")
    void openRejectsClosedQuestion() {
        ReputationHarness h = harness();
        h.qa.withQuestion(7L, 100L, 5L, 21L, false);

        assertThatThrownBy(() -> h.bountyService.open(TEACHER, 7L, 100, 3))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.code()).isEqualTo("REP-1001"));
    }

    @Test
    @DisplayName("问题不存在时返回 404 QA-4001，沿用上游错误码")
    void openRejectsUnknownQuestion() {
        ReputationHarness h = harness();

        assertThatThrownBy(() -> h.bountyService.open(TEACHER, 404L, 100, 3))
                .isInstanceOfSatisfying(NotFoundException.class,
                        ex -> assertThat(ex.code()).isEqualTo("QA-4001"));
    }

    @Test
    @DisplayName("采纳结算：冻结池扣掉，赢家按悬赏额入账声誉")
    void settleAwardsWinner() {
        ReputationHarness h = harness();
        h.bountyService.open(TEACHER, 7L, 100, 3);

        h.bountyService.settleOnAccept(7L, 21L, 100L, "evt-accept-1");

        ReputationAccount sponsor = h.accounts.get(9L);
        assertThat(sponsor.frozenPoints()).isZero();
        assertThat(sponsor.availablePoints()).isEqualTo(900);
        assertThat(sponsor.reputation()).isZero();
        assertThat(h.accounts.get(100L).reputation()).isEqualTo(100);
        assertThat(h.ledger.countByUserAndReason(100L, ReputationReason.BOUNTY_AWARDED)).isEqualTo(1);
        assertThat(h.events.ofType(BountyAwardedEvent.class)).hasSize(1);
        assertThat(h.audit.actions).containsExactly("BOUNTY_OPENED", "BOUNTY_AWARDED");
    }

    @Test
    @DisplayName("发起人自己的回答被采纳：不能拿自己的悬赏，按关闭退款处理")
    void settleRefundsWhenSponsorWinsOwnBounty() {
        ReputationHarness h = harness();
        h.bountyService.open(TEACHER, 7L, 100, 3);

        h.bountyService.settleOnAccept(7L, 21L, 9L, "evt-accept-1");

        ReputationAccount sponsor = h.accounts.get(9L);
        assertThat(sponsor.frozenPoints()).isZero();
        assertThat(sponsor.availablePoints()).isEqualTo(1000);
        assertThat(sponsor.reputation()).isZero();
        assertThat(h.bounties.findLatestByQuestion(7L))
                .get().extracting(Bounty::status).isEqualTo(BountyStatus.EXPIRED);
        assertThat(h.events.ofType(BountyClosedEvent.class)).hasSize(1);
        assertThat(h.events.ofType(BountyAwardedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("重复结算不会重复加分：悬赏已结束后直接返回")
    void settleIsIdempotent() {
        ReputationHarness h = harness();
        h.bountyService.open(TEACHER, 7L, 100, 3);
        h.bountyService.settleOnAccept(7L, 21L, 100L, "evt-accept-1");

        h.bountyService.settleOnAccept(7L, 21L, 100L, "evt-accept-1");

        assertThat(h.accounts.get(100L).reputation()).isEqualTo(100);
        assertThat(h.ledger.entries()).hasSize(1);
        assertThat(h.events.ofType(BountyAwardedEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("问题没有悬赏时，采纳事件不会产生任何副作用")
    void settleWithoutBountyDoesNothing() {
        ReputationHarness h = harness();

        h.bountyService.settleOnAccept(7L, 21L, 100L, "evt-accept-1");

        assertThat(h.accounts.get(100L).reputation()).isZero();
        assertThat(h.ledger.entries()).isEmpty();
        assertThat(h.events.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("发起人账户缺失时不凭空建账户，悬赏保持原状等对账介入")
    void settleSkipsWhenSponsorAccountMissing() {
        ReputationHarness h = new ReputationHarness(NOW.toString());
        Bounty bounty = Bounty.reconstitute(1L, 7L, 9L, 100, BountyStatus.OPEN,
                NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(2)), null, null, null);
        h.bounties.with(bounty);

        h.bountyService.settleOnAccept(7L, 21L, 100L, "evt-accept-1");

        assertThat(bounty.status()).isEqualTo(BountyStatus.OPEN);
        assertThat(h.accounts.findByUserId(9L)).isEmpty();
        assertThat(h.accounts.findByUserId(100L)).isEmpty();
        assertThat(h.events.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("到期扫描跳过发起人账户缺失的悬赏，不中断其余处理")
    void expireDueSkipsMissingSponsor() {
        ReputationHarness h = new ReputationHarness(NOW.toString());
        Bounty orphan = Bounty.reconstitute(1L, 7L, 9L, 100, BountyStatus.OPEN,
                NOW.minus(Duration.ofDays(5)), NOW.minus(Duration.ofDays(1)), null, null, null);
        h.bounties.with(orphan);

        assertThat(h.bountyService.expireDue()).isZero();
        assertThat(orphan.status()).isEqualTo(BountyStatus.OPEN);
        assertThat(h.events.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("到期扫描：关闭悬赏并退回冻结积分，返回处理条数")
    void expireDueRefundsSponsor() {
        ReputationHarness h = harness();
        Bounty due = Bounty.reconstitute(1L, 7L, 9L, 100, BountyStatus.OPEN,
                NOW.minus(Duration.ofDays(5)), NOW.minus(Duration.ofDays(1)), null, null, null);
        h.bounties.with(due);
        h.accounts.save(ReputationAccount.reconstitute(9L, 0, 900, 100, 0L));

        int processed = h.bountyService.expireDue();

        assertThat(processed).isEqualTo(1);
        assertThat(due.status()).isEqualTo(BountyStatus.EXPIRED);
        assertThat(h.accounts.get(9L).frozenPoints()).isZero();
        assertThat(h.accounts.get(9L).availablePoints()).isEqualTo(1000);
        assertThat(h.events.ofType(BountyClosedEvent.class)).hasSize(1);
    }

    @Test
    @DisplayName("未到期的悬赏不会被扫描到")
    void expireDueSkipsBountyStillOpen() {
        ReputationHarness h = harness();
        h.bountyService.open(TEACHER, 7L, 100, 3);

        assertThat(h.bountyService.expireDue()).isZero();
        assertThat(h.accounts.get(9L).frozenPoints()).isEqualTo(100);
    }

    @Test
    @DisplayName("查询最近一次悬赏：带回发起人显示名与状态字符串")
    void latestReturnsView() {
        ReputationHarness h = harness();
        h.bountyService.open(TEACHER, 7L, 100, 3);

        assertThat(h.bountyService.latest(7L)).get().satisfies(view -> {
            assertThat(view.sponsorId()).isEqualTo(9L);
            assertThat(view.sponsorName()).isEqualTo("王老师");
            assertThat(view.points()).isEqualTo(100);
            assertThat(view.status()).isEqualTo("OPEN");
            assertThat(view.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(3)));
        });
    }

    @Test
    @DisplayName("没有悬赏时查询返回空 Optional（控制器据此回 204）")
    void latestEmptyWhenNoBounty() {
        assertThat(harness().bountyService.latest(7L)).isEmpty();
    }
}
