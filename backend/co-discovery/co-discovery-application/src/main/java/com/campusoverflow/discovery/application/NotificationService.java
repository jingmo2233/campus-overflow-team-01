package com.campusoverflow.discovery.application;

import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationRepository;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：通知中心（列表、未读数、已读）。 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public PageResult<NotificationView> list(Actor actor, PageRequest page) {
        return notifications.findByRecipient(actor.userId(), page).map(NotificationService::toView);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Actor actor) {
        return notifications.countUnread(actor.userId());
    }

    @Transactional
    public void markRead(Actor actor, long notificationId) {
        Notification notification = notifications.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("DIS-4001", "通知不存在"));
        notification.markRead(actor.userId());
        notifications.save(notification);
    }

    @Transactional
    public int markAllRead(Actor actor) {
        return notifications.markAllRead(actor.userId());
    }

    private static NotificationView toView(Notification n) {
        return new NotificationView(n.id(), n.type().name(), n.title(), n.link(), n.read(), n.createdAt());
    }
}
