package com.campusoverflow.reputation.application;

import com.campusoverflow.reputation.api.event.BadgeAwardedEvent;
import com.campusoverflow.reputation.domain.BadgeAward;
import com.campusoverflow.reputation.domain.BadgeRepository;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 徽章授予（幂等：已拥有则忽略）。 */
@Service
public class BadgeService {

    private final BadgeRepository badges;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public BadgeService(BadgeRepository badges, IntegrationEventPublisher events, Clock clock) {
        this.badges = badges;
        this.events = events;
        this.clock = clock;
    }

    /**
     * 授予徽章。存在性检查放在最前面，因此「提交第一个回答」「重复认证同一回答」这类
     * 会被反复触发的事件，重复调用不会重复发徽章、也不会重复发事件。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void grant(long userId, BadgeType type) {
        if (badges.has(userId, type)) {
            return;
        }
        Instant now = clock.instant();
        badges.award(new BadgeAward(userId, type, now));
        events.publish(new BadgeAwardedEvent(EventIds.next(), now, userId, type.name(), type.displayName()));
    }
}
