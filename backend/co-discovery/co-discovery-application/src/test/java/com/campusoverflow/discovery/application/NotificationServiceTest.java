package com.campusoverflow.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.discovery.application.fake.DiscoveryTestFixtures;
import com.campusoverflow.discovery.application.fake.DiscoveryTestFixtures.InMemoryNotifications;
import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationType;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private static final Instant T = DiscoveryTestFixtures.at("2026-10-27T10:00:00Z");
    private static final Actor OWNER = new Actor(7L, "小张", Role.STUDENT);
    private static final Actor OTHER = new Actor(8L, "路人", Role.STUDENT);

    private InMemoryNotifications repository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryNotifications();
        service = new NotificationService(repository);
        repository.save(Notification.create(7L, NotificationType.ANSWER_RECEIVED, "有人回答了你的问题",
                "/questions/11", "evt-1", T));
        repository.save(Notification.create(7L, NotificationType.BADGE_AWARDED, "获得新徽章：首答",
                "/me/badges", "evt-2", T));
        repository.save(Notification.create(8L, NotificationType.MENTIONED, "有人提到了你",
                "/questions/11", "evt-3", T));
        repository.unread = 2;
    }

    @Test
    @DisplayName("列表只返回自己的通知，并映射成视图")
    void listReturnsOwnNotificationsAsViews() {
        var page = service.list(OWNER, new PageRequest(1, 20));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(NotificationView::type)
                .containsExactly("ANSWER_RECEIVED", "BADGE_AWARDED");
        assertThat(page.items().get(0).read()).isFalse();
        assertThat(page.items().get(0).createdAt()).isEqualTo(T);
    }

    @Test
    @DisplayName("分页偏移生效，第二条在第一页之外")
    void listHonoursPaging() {
        var page = service.list(OWNER, new PageRequest(2, 1));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).type()).isEqualTo("BADGE_AWARDED");
        assertThat(page.total()).isEqualTo(2);
    }

    @Test
    @DisplayName("未读数直接取仓库计数")
    void unreadCountDelegatesToRepository() {
        assertThat(service.unreadCount(OWNER)).isEqualTo(2);
    }

    @Test
    @DisplayName("本人标记已读：状态翻转并回写")
    void markReadByOwnerFlipsFlag() {
        long id = repository.saved.get(0).id();

        service.markRead(OWNER, id);

        assertThat(repository.findById(id).orElseThrow().read()).isTrue();
    }

    @Test
    @DisplayName("越权标记别人的通知：403")
    void markReadRejectsOtherUser() {
        long id = repository.saved.get(0).id();

        assertThatThrownBy(() -> service.markRead(OTHER, id))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("只能标记自己的通知");
    }

    @Test
    @DisplayName("通知不存在：404 且带 DIS-4001")
    void markReadUnknownNotification() {
        assertThatThrownBy(() -> service.markRead(OWNER, 999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("通知不存在");
    }

    @Test
    @DisplayName("全部已读：返回实际更新条数，且不动别人的通知")
    void markAllReadOnlyTouchesOwn() {
        int updated = service.markAllRead(OWNER);

        assertThat(updated).isEqualTo(2);
        assertThat(repository.findById(repository.saved.get(1).id()).orElseThrow().read()).isTrue();
        assertThat(repository.findById(repository.saved.get(2).id()).orElseThrow().read()).isFalse();
    }
}
