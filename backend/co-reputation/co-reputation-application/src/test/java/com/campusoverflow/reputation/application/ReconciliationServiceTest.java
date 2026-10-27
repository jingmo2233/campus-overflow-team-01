package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.reputation.application.fake.ReputationHarness;
import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationReason;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReconciliationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");

    @Test
    @DisplayName("FF-8：账户声誉与流水汇总一致时无差异")
    void consistentLedgerReportsNothing() {
        ReputationHarness h = new ReputationHarness(NOW.toString());
        h.accounts.with(ReputationAccount.reconstitute(100L, 25, 0, 0, 0L));
        h.ledger.append(LedgerEntry.of(100L, "evt-1", ReputationReason.ANSWER_VOTE, 10, "ANSWER", 21L, NOW));
        h.ledger.append(LedgerEntry.of(100L, "evt-2", ReputationReason.ANSWER_ACCEPTED, 15, "ANSWER", 22L, NOW));

        assertThat(h.reconciliation.reconcile()).isEmpty();
    }

    @Test
    @DisplayName("账户声誉被外部改动时能被检出是哪一位、差多少")
    void mismatchIsReported() {
        ReputationHarness h = new ReputationHarness(NOW.toString());
        h.accounts.with(ReputationAccount.reconstitute(100L, 30, 0, 0, 0L));
        h.ledger.append(LedgerEntry.of(100L, "evt-1", ReputationReason.ANSWER_VOTE, 10, "ANSWER", 21L, NOW));

        List<ReconciliationService.Mismatch> mismatches = h.reconciliation.reconcile();

        assertThat(mismatches).singleElement().satisfies(m -> {
            assertThat(m.userId()).isEqualTo(100L);
            assertThat(m.accountReputation()).isEqualTo(30);
            assertThat(m.ledgerSum()).isEqualTo(10L);
        });
    }

    @Test
    @DisplayName("新开户但还没有流水时按 0 比对，不算差异")
    void freshAccountIsClean() {
        ReputationHarness h = new ReputationHarness(NOW.toString());
        h.accounts.with(ReputationAccount.open(9L, 1000));

        assertThat(h.reconciliation.reconcile()).isEmpty();
    }
}
