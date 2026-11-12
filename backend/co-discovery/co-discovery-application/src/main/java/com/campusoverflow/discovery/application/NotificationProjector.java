package com.campusoverflow.discovery.application;

import com.campusoverflow.discovery.domain.notification.MentionParser;
import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationRepository;
import com.campusoverflow.discovery.domain.notification.NotificationType;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.CommentPostedEvent;
import com.campusoverflow.reputation.api.event.BadgeAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 通知中心：把上游事件转换为站内通知。幂等键 (sourceEventId, recipientId)。 */
@Component
public class NotificationProjector {

    private final NotificationRepository notifications;
    private final IdentityApi identity;
    private final QaQueryApi qa;

    public NotificationProjector(NotificationRepository notifications, IdentityApi identity, QaQueryApi qa) {
        this.notifications = notifications;
        this.identity = identity;
        this.qa = qa;
    }

    @EventListener
    @Transactional
    public void on(AnswerSubmittedEvent e) {
        if (e.answerAuthorId() == e.questionAuthorId()) {
            return;
        }
        notify(e.questionAuthorId(), NotificationType.ANSWER_RECEIVED,
                name(e.answerAuthorId()) + " 回答了你的问题：" + e.questionTitle(),
                "/questions/" + e.questionId(), e.eventId(), e.occurredAt());
    }

    @EventListener
    @Transactional
    public void on(AnswerAcceptedEvent e) {
        if (e.answerAuthorId() == e.questionAuthorId()) {
            return;
        }
        notify(e.answerAuthorId(), NotificationType.ANSWER_ACCEPTED,
                "你的回答被采纳：" + e.questionTitle(),
                "/questions/" + e.questionId() + "#answer-" + e.answerId(), e.eventId(), e.occurredAt());
    }

    @EventListener
    @Transactional
    public void on(AnswerEndorsedEvent e) {
        if (e.answerAuthorId() == e.endorserId()) {
            return;
        }
        notify(e.answerAuthorId(), NotificationType.ANSWER_ENDORSED,
                "你的回答获得教师认证：" + e.questionTitle(),
                "/questions/" + e.questionId() + "#answer-" + e.answerId(), e.eventId(), e.occurredAt());
    }

    /** 评论里的 @昵称 逐个提醒（已去重、上限 5 人）。 */
    @EventListener
    @Transactional
    public void on(CommentPostedEvent e) {
        for (String nickname : MentionParser.extract(e.body())) {
            identity.findByDisplayName(nickname)
                    .filter(user -> user.id() != e.authorId())
                    .ifPresent(user -> notify(user.id(), NotificationType.MENTIONED,
                            name(e.authorId()) + " 在评论中提到了你",
                            "/questions/" + e.questionId(), e.eventId(), e.occurredAt()));
        }
    }

    @EventListener
    @Transactional
    public void on(BountyAwardedEvent e) {
        notify(e.winnerId(), NotificationType.BOUNTY_AWARDED,
                "你赢得了悬赏：" + e.points() + " 积分",
                "/questions/" + e.questionId(), e.eventId(), e.occurredAt());
    }

    @EventListener
    @Transactional
    public void on(BadgeAwardedEvent e) {
        notify(e.userId(), NotificationType.BADGE_AWARDED,
                "你获得了新徽章：" + e.badgeName(),
                "/users/" + e.userId() + "/reputation", e.eventId(), e.occurredAt());
    }

    /** 幂等键 (sourceEventId, recipientId)：同一事件重复投递不会产生重复通知。 */
    private void notify(long recipientId, NotificationType type, String title, String link, String eventId,
                        java.time.Instant at) {
        if (notifications.exists(eventId, recipientId)) {
            return;
        }
        notifications.save(Notification.create(recipientId, type, title, link, eventId, at));
    }

    private String name(long userId) {
        return identity.findUser(userId).map(UserSummary::displayName).orElse("某位同学");
    }
}
