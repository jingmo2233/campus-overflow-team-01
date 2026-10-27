package com.campusoverflow.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.reputation.api.event.BadgeAwardedEvent;
import com.campusoverflow.reputation.application.fake.ReputationHarness;
import com.campusoverflow.reputation.domain.BadgeType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BadgeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T08:00:00Z");

    @Test
    @DisplayName("授予徽章会落库并发出 BadgeAwarded")
    void grantPersistsAndPublishes() {
        ReputationHarness h = new ReputationHarness(NOW.toString());

        h.badgeService.grant(100L, BadgeType.FIRST_ANSWER);

        assertThat(h.badges.has(100L, BadgeType.FIRST_ANSWER)).isTrue();
        assertThat(h.events.ofType(BadgeAwardedEvent.class)).singleElement().satisfies(e -> {
            assertThat(e.userId()).isEqualTo(100L);
            assertThat(e.badgeCode()).isEqualTo("FIRST_ANSWER");
            assertThat(e.badgeName()).isEqualTo("初来乍到");
        });
    }

    @Test
    @DisplayName("已拥有的徽章不再重复授予，也不重复发事件")
    void grantIsIdempotent() {
        ReputationHarness h = new ReputationHarness(NOW.toString());

        h.badgeService.grant(100L, BadgeType.FIRST_ANSWER);
        h.badgeService.grant(100L, BadgeType.FIRST_ANSWER);

        assertThat(h.badges.findByUser(100L)).hasSize(1);
        assertThat(h.events.ofType(BadgeAwardedEvent.class)).hasSize(1);
    }
}
