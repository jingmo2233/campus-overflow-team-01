package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.reputation.application.fake.ReputationHarness;
import com.campusoverflow.reputation.domain.BadgeAward;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReputationQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");
    private static final Actor OWNER = new Actor(100L, "李四", Role.STUDENT);
    private static final Actor STRANGER = new Actor(200L, "张三", Role.STUDENT);
    private static final Actor ADMIN = new Actor(1L, "管理员", Role.ADMIN);

    private ReputationHarness harness() {
        ReputationHarness h = new ReputationHarness(NOW.toString());
        h.identity.withUser(100L, "李四", Role.STUDENT)
                .withUser(200L, "张三", Role.STUDENT)
                .withUser(1L, "管理员", Role.ADMIN);
        h.accounts.with(ReputationAccount.reconstitute(100L, 120, 30, 100, 2L));
        return h;
    }

    @Test
    @DisplayName("本人可以看积分，他人只能看声誉与徽章")
    void pointsVisibleOnlyToOwnerOrAdmin() {
        ReputationHarness h = harness();

        ReputationProfileView self = h.queryService.profile(100L, OWNER);
        ReputationProfileView stranger = h.queryService.profile(100L, STRANGER);
        ReputationProfileView admin = h.queryService.profile(100L, ADMIN);
        ReputationProfileView anonymous = h.queryService.profile(100L, null);

        assertThat(self.reputation()).isEqualTo(120);
        assertThat(self.availablePoints()).isEqualTo(30);
        assertThat(self.frozenPoints()).isEqualTo(100);
        assertThat(stranger.availablePoints()).isNull();
        assertThat(stranger.frozenPoints()).isNull();
        assertThat(stranger.reputation()).isEqualTo(120);
        assertThat(admin.availablePoints()).isEqualTo(30);
        assertThat(anonymous.availablePoints()).isNull();
    }

    @Test
    @DisplayName("徽章按类型翻译成展示名与说明")
    void badgesAreTranslated() {
        ReputationHarness h = harness();
        h.badges.award(new BadgeAward(100L, BadgeType.FIRST_ACCEPTED, NOW));

        ReputationProfileView view = h.queryService.profile(100L, OWNER);

        assertThat(view.badges()).singleElement().satisfies(badge -> {
            assertThat(badge.code()).isEqualTo("FIRST_ACCEPTED");
            assertThat(badge.name()).isEqualTo("初露锋芒");
            assertThat(badge.description()).isEqualTo("第一个回答被采纳");
            assertThat(badge.awardedAt()).isEqualTo(NOW);
        });
    }

    @Test
    @DisplayName("账户还没开立时按 0 声誉返回，而不是 404")
    void accountlessUserReportsZero() {
        ReputationHarness h = harness();

        ReputationProfileView view = h.queryService.profile(200L, STRANGER);

        assertThat(view.reputation()).isZero();
        assertThat(view.availablePoints()).isZero();
        assertThat(view.badges()).isEmpty();
    }

    @Test
    @DisplayName("用户本身不存在才返回 404")
    void unknownUserIsNotFound() {
        ReputationHarness h = harness();

        assertThatThrownBy(() -> h.queryService.profile(777L, ADMIN))
                .isInstanceOfSatisfying(NotFoundException.class,
                        ex -> assertThat(ex.code()).isEqualTo("ID-4001"));
    }

    @Test
    @DisplayName("流水属于个人数据：他人查看抛 403 REP-3002")
    void ledgerForbiddenForStrangers() {
        ReputationHarness h = harness();

        assertThatThrownBy(() -> h.queryService.ledger(100L, STRANGER, new PageRequest(1, 20)))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        ex -> assertThat(ex.code()).isEqualTo("REP-3002"));
    }

    @Test
    @DisplayName("未登录查看流水同样被拒绝")
    void ledgerForbiddenForAnonymous() {
        ReputationHarness h = harness();

        assertThatThrownBy(() -> h.queryService.ledger(100L, null, new PageRequest(1, 20)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("本人与管理员可以查看流水，条目带中文原因标签")
    void ledgerVisibleToOwnerAndAdmin() {
        ReputationHarness h = harness();
        h.ledger.append(LedgerEntry.of(100L, "evt-1", ReputationReason.ANSWER_VOTE, 10, "ANSWER", 21L, NOW));
        h.ledger.append(LedgerEntry.of(100L, "evt-2", ReputationReason.ANSWER_ACCEPTED, 15, "ANSWER", 22L, NOW));

        PageResult<LedgerEntryView> own = h.queryService.ledger(100L, OWNER, new PageRequest(1, 20));
        PageResult<LedgerEntryView> byAdmin = h.queryService.ledger(100L, ADMIN, new PageRequest(1, 20));

        assertThat(own.total()).isEqualTo(2);
        assertThat(byAdmin.total()).isEqualTo(2);
        assertThat(own.items()).extracting(LedgerEntryView::reasonLabel)
                .containsExactly("回答被采纳", "回答被投票");
        assertThat(own.items()).extracting(LedgerEntryView::refType).containsOnly("ANSWER");
    }
}
