package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.reputation.api.event.ReputationChangedEvent;
import com.campusoverflow.reputation.application.fake.ReputationHarness;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LeaderboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");

    private ReputationHarness harness() {
        ReputationHarness h = new ReputationHarness(NOW.toString());
        h.identity.withUser(100L, "李四", Role.STUDENT).withUser(9L, "王老师", Role.TEACHER);
        return h;
    }

    @Test
    @DisplayName("声誉变化写入排行榜；同一事件重复投递只写一次")
    void incrementsOncePerEvent() {
        ReputationHarness h = harness();
        ReputationChangedEvent e = new ReputationChangedEvent("evt-1", NOW, 100L, 10, 10, "ANSWER_VOTE");

        h.leaderboardService.on(e);
        h.leaderboardService.on(e);

        assertThat(h.leaderboard.increments).hasSize(1);
        assertThat(h.leaderboard.increments.get(0).userId()).isEqualTo(100L);
        assertThat(h.leaderboard.increments.get(0).score()).isEqualTo(10);
    }

    @Test
    @DisplayName("总榜直接读账户表，不使用 Redis 里的分值")
    void allPeriodReadsAccountTable() {
        ReputationHarness h = harness();
        h.accounts.with(ReputationAccount.reconstitute(9L, 100, 900, 100, 0L))
                .with(ReputationAccount.reconstitute(100L, 300, 0, 0, 0L));
        h.leaderboard.withBoard(LeaderboardPeriod.ALL, new LeaderboardStore.Entry(100L, 9999));

        List<LeaderboardEntryView> top = h.leaderboardService.top(LeaderboardPeriod.ALL, 10);

        assertThat(top).extracting(LeaderboardEntryView::userId).containsExactly(100L, 9L);
        assertThat(top).extracting(LeaderboardEntryView::score).containsExactly(300L, 100L);
        assertThat(top).extracting(LeaderboardEntryView::rank).containsExactly(1, 2);
    }

    @Test
    @DisplayName("周榜读 Redis 分值并按名次补用户资料")
    void weekPeriodUsesStoreAndLoadsNames() {
        ReputationHarness h = harness();
        h.leaderboard.withBoard(LeaderboardPeriod.WEEK,
                new LeaderboardStore.Entry(100L, 88), new LeaderboardStore.Entry(9L, 66));

        List<LeaderboardEntryView> top = h.leaderboardService.top(LeaderboardPeriod.WEEK, 10);

        assertThat(top).extracting(LeaderboardEntryView::displayName).containsExactly("李四", "王老师");
        assertThat(top).extracting(LeaderboardEntryView::rank).containsExactly(1, 2);
        assertThat(top).extracting(LeaderboardEntryView::verified).containsExactly(true, true);
    }

    @Test
    @DisplayName("账号已注销时名次保留、显示名为 null，榜单不错位")
    void unknownUserKeepsRank() {        ReputationHarness h = harness();
        h.leaderboard.withBoard(LeaderboardPeriod.WEEK, new LeaderboardStore.Entry(999L, 5));

        List<LeaderboardEntryView> top = h.leaderboardService.top(LeaderboardPeriod.WEEK, 10);

        assertThat(top).singleElement().satisfies(view -> {
            assertThat(view.rank()).isEqualTo(1);
            assertThat(view.displayName()).isNull();
            assertThat(view.verified()).isFalse();
            assertThat(view.score()).isEqualTo(5L);
        });
    }

    @Test
    @DisplayName("limit 低于 1 夹到 1，高于 50 夹到 50")
    void limitIsClamped() {
        ReputationHarness h = harness();
        h.leaderboard.withBoard(LeaderboardPeriod.WEEK,
                new LeaderboardStore.Entry(100L, 88), new LeaderboardStore.Entry(9L, 66));

        assertThat(h.leaderboardService.top(LeaderboardPeriod.WEEK, 0)).hasSize(1);
        assertThat(h.leaderboardService.top(LeaderboardPeriod.WEEK, 999)).hasSize(2);
    }

    @Test
    @DisplayName("空榜返回空列表，不触发用户查询")
    void emptyBoardReturnsEmptyList() {
        ReputationHarness h = harness();

        assertThat(h.leaderboardService.top(LeaderboardPeriod.MONTH, 10)).isEmpty();
    }

    @Test
    @DisplayName("未通过学校认证的账号在榜上标记为未认证")
    void unverifiedUserFlagged() {
        ReputationHarness h = new ReputationHarness(NOW.toString());
        h.identity.withUnverifiedUser(100L, "李四", Role.STUDENT);
        h.leaderboard.withBoard(LeaderboardPeriod.WEEK, new LeaderboardStore.Entry(100L, 10));

        assertThat(h.leaderboardService.top(LeaderboardPeriod.WEEK, 10)).singleElement()
                .satisfies(view -> {
                    assertThat(view.displayName()).isEqualTo("李四");
                    assertThat(view.verified()).isFalse();
                });
    }
}
