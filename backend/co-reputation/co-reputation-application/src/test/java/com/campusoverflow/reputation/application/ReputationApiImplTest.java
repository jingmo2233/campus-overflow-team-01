package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.reputation.api.ReputationSummary;
import com.campusoverflow.reputation.application.fake.ReputationHarness;
import com.campusoverflow.reputation.domain.ReputationAccount;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReputationApiImplTest {

    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");

    @Test
    @DisplayName("对外摘要：已开户返回真实声誉，未开户返回 0 而不是报错")
    void summaryHandlesUnknownUser() {
        ReputationHarness h = new ReputationHarness(NOW.toString());
        h.accounts.with(ReputationAccount.reconstitute(100L, 42, 0, 0, 0L));

        assertThat(h.reputationApi.summary(100L)).isEqualTo(new ReputationSummary(100L, 42));
        assertThat(h.reputationApi.summary(999L)).isEqualTo(new ReputationSummary(999L, 0));
    }

    @Test
    @DisplayName("批量查询对每个入参 ID 都有值，缺失的补 0")
    void reputationsFillMissingWithZero() {
        ReputationHarness h = new ReputationHarness(NOW.toString());
        h.accounts.with(ReputationAccount.reconstitute(100L, 42, 0, 0, 0L))
                .with(ReputationAccount.reconstitute(200L, 7, 0, 0, 0L));

        Map<Long, Integer> result = h.reputationApi.reputations(List.of(100L, 200L, 300L));

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(100L, 42, 200L, 7, 300L, 0));
    }

    @Test
    @DisplayName("空集合直接返回空 Map")
    void reputationsOfEmptyCollection() {
        ReputationHarness h = new ReputationHarness(NOW.toString());

        assertThat(h.reputationApi.reputations(List.of())).isEmpty();
    }
}
