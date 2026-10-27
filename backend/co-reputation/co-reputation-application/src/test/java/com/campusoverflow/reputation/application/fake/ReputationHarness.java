package com.campusoverflow.reputation.application.fake;

import com.campusoverflow.reputation.application.BadgeService;
import com.campusoverflow.reputation.application.BountyService;
import com.campusoverflow.reputation.application.LeaderboardService;
import com.campusoverflow.reputation.application.ReconciliationService;
import com.campusoverflow.reputation.application.ReputationApiImpl;
import com.campusoverflow.reputation.application.ReputationEventHandlers;
import com.campusoverflow.reputation.application.ReputationLedgerService;
import com.campusoverflow.reputation.application.ReputationQueryService;
import java.time.Clock;

/**
 * 把一个完整装配好的声誉上下文摆出来：所有端口都是内存替身，服务是真实实现。
 * 测试只需要替换「哪一个替身里放了什么」，不必重复写构造参数。
 */
public final class ReputationHarness {

    public final ReputationTestFixtures.InMemoryAccounts accounts = new ReputationTestFixtures.InMemoryAccounts();
    public final ReputationTestFixtures.InMemoryLedger ledger = new ReputationTestFixtures.InMemoryLedger();
    public final ReputationTestFixtures.InMemoryBadges badges = new ReputationTestFixtures.InMemoryBadges();
    public final ReputationTestFixtures.InMemoryBounties bounties = new ReputationTestFixtures.InMemoryBounties();
    public final ReputationTestFixtures.RecordingEvents events = new ReputationTestFixtures.RecordingEvents();
    public final ReputationTestFixtures.RecordingAudit audit = new ReputationTestFixtures.RecordingAudit();
    public final ReputationTestFixtures.StubIdentity identity = new ReputationTestFixtures.StubIdentity();
    public final ReputationTestFixtures.StubQa qa = new ReputationTestFixtures.StubQa();
    public final ReputationTestFixtures.InMemoryLeaderboard leaderboard =
            new ReputationTestFixtures.InMemoryLeaderboard();
    public final ReputationTestFixtures.InMemoryProcessed processed =
            new ReputationTestFixtures.InMemoryProcessed();
    public final Clock clock;

    public final BadgeService badgeService;
    public final ReputationLedgerService ledgerService;
    public final BountyService bountyService;
    public final ReputationEventHandlers handlers;
    public final LeaderboardService leaderboardService;
    public final ReputationQueryService queryService;
    public final ReputationApiImpl reputationApi;
    public final ReconciliationService reconciliation;

    public ReputationHarness(String isoInstant) {
        this.clock = ReputationTestFixtures.fixedClock(isoInstant);
        this.badgeService = new BadgeService(badges, events, clock);
        this.ledgerService = new ReputationLedgerService(accounts, ledger, badgeService, events, clock);
        this.bountyService = new BountyService(bounties, accounts, ledgerService, qa, identity, events, audit, clock);
        this.handlers = new ReputationEventHandlers(accounts, ledger, ledgerService, badgeService, bountyService, clock);
        this.leaderboardService = new LeaderboardService(leaderboard, accounts, identity, processed, clock);
        this.queryService = new ReputationQueryService(accounts, ledger, badges, identity);
        this.reputationApi = new ReputationApiImpl(accounts);
        this.reconciliation = new ReconciliationService(accounts, ledger);
    }
}
