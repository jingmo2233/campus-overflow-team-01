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

    static final String CONSUMER = "reputation.leaderboard";

    /** 声誉变化异步更新周/月/学期榜；重复事件由 ProcessedEventStore 拦住。 */
    @EventListener
    @Transactional
    public void on(ReputationChangedEvent e) {
        if (!processed.markProcessed(e.eventId(), CONSUMER)) {
            return;
        }
        store.increment(e.userId(), e.delta(), e.occurredAt());
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryView> top(LeaderboardPeriod period, int limit) {
        int size = Math.min(Math.max(limit, 1), 100);
        if (period == LeaderboardPeriod.ALL) {
            List<ReputationAccount> accountsTop = accounts.topByReputation(size);
            Map<Long, UserSummary> users = identity.findUsers(accountsTop.stream()
                    .map(ReputationAccount::userId).toList());
            List<LeaderboardEntryView> views = new ArrayList<>();
            int rank = 1;
            for (ReputationAccount account : accountsTop) {
                UserSummary user = users.get(account.userId());
                views.add(new LeaderboardEntryView(rank++, account.userId(),
                        user == null ? "未知用户" : user.displayName(), user != null && user.verified(),
                        account.reputation()));
            }
            return views;
        }
        List<LeaderboardStore.Entry> entries = store.top(period, clock.instant(), size);
        Map<Long, UserSummary> users = identity.findUsers(entries.stream()
                .map(LeaderboardStore.Entry::userId).toList());
        List<LeaderboardEntryView> views = new ArrayList<>();
        int rank = 1;
        for (LeaderboardStore.Entry entry : entries) {
            UserSummary user = users.get(entry.userId());
            views.add(new LeaderboardEntryView(rank++, entry.userId(),
                    user == null ? "未知用户" : user.displayName(), user != null && user.verified(), entry.score()));
        }
        return views;
    }
}
