package com.campusoverflow.reputation.application;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.reputation.api.event.ReputationChangedEvent;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.shared.event.ProcessedEventStore;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 排行榜：周/月/学期榜来自 Redis ZSET，总榜直接读账户表。 */
@Service
public class LeaderboardService {

    /** 与 PageRequest.MAX_SIZE 一致，防止一次拉取过多用户资料。 */
    private static final int MAX_LIMIT = 50;
    private static final String CONSUMER = "leaderboard";

    private final LeaderboardStore store;
    private final ReputationAccountRepository accounts;
    private final IdentityApi identity;
    private final ProcessedEventStore processed;
    private final Clock clock;

    public LeaderboardService(LeaderboardStore store, ReputationAccountRepository accounts, IdentityApi identity,
                              ProcessedEventStore processed, Clock clock) {
        this.store = store;
        this.accounts = accounts;
        this.identity = identity;
        this.processed = processed;
        this.clock = clock;
    }

    /**
     * 把声誉增量同步进排行榜。事件是至少一次投递，用 ProcessedEventStore 按 eventId 去重，
     * 否则重复投递会把同一次加分累加两遍——排行榜分数虚高且无从对账。
     */
    @EventListener
    @Transactional
    public void on(ReputationChangedEvent e) {
        if (!processed.markProcessed(e.eventId(), CONSUMER)) {
            return;
        }
        store.increment(e.userId(), e.delta(), clock.instant());
    }

    /**
     * 取榜单。ALL 直接读账户表，因为总榜口径就是「当前声誉值」，读缓存反而会与账户表长期漂移；
     * 周/月/学期是区间增量，只能靠 ZSET 的分键累加得到。
     */
    @Transactional(readOnly = true)
    public List<LeaderboardEntryView> top(LeaderboardPeriod period, int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
        if (period == LeaderboardPeriod.ALL) {
            List<LeaderboardStore.Entry> all = accounts.topByReputation(capped).stream()
                    .map(account -> new LeaderboardStore.Entry(account.userId(), account.reputation()))
                    .toList();
            return toView(all);
        }
        return toView(store.top(period, clock.instant(), capped));
    }

    /**
     * 批量补用户资料，避免逐条查询造成的 N+1。用户已被删除时 displayName 为 null，
     * 名次与分数照常保留——榜单不该因为一个账号注销就错位。
     */
    private List<LeaderboardEntryView> toView(List<LeaderboardStore.Entry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Map<Long, UserSummary> users = identity.findUsers(
                entries.stream().map(LeaderboardStore.Entry::userId).toList());
        List<LeaderboardEntryView> views = new ArrayList<>(entries.size());
        int rank = 1;
        for (LeaderboardStore.Entry entry : entries) {
            UserSummary user = users.get(entry.userId());
            views.add(new LeaderboardEntryView(rank++, entry.userId(),
                    user == null ? null : user.displayName(),
                    user != null && user.verified(),
                    entry.score()));
        }
        return views;
    }
}
