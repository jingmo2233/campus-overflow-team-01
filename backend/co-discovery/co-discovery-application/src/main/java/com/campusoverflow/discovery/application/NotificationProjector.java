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
import java.time.Instant;
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
        // INV-N2：自己回答自己的问题不产生通知
        if (e.answerAuthorId() == e.questionAuthorId()) {
            return;
        }
        notify(e.questionAuthorId(), NotificationType.ANSWER_RECEIVED,
                name(e.answerAuthorId()) + " 回答了你的问题「" + e.questionTitle() + "」",
                link(e.questionId()), e.eventId(), e.occurredAt());
    }

    @EventListener
    @Transactional
    public void on(AnswerAcceptedEvent e) {
        // INV-N2：自己采纳自己的回答不发通知
        if (e.answerAuthorId() == e.acceptedById()) {
            return;
        }
        notify(e.answerAuthorId(), NotificationType.ANSWER_ACCEPTED,
                "你的回答被采纳：「" + e.questionTitle() + "」",
                link(e.questionId()), e.eventId(), e.occurredAt());
    }

    @EventListener
    @Transactional
    public void on(AnswerEndorsedEvent e) {
        if (e.answerAuthorId() == e.endorserId()) {
            return;
        }
        notify(e.answerAuthorId(), NotificationType.ANSWER_ENDORSED,
                name(e.endorserId()) + " 认证了你的回答：「" + e.questionTitle() + "」",
                link(e.questionId()), e.eventId(), e.occurredAt());
    }

    @EventListener
    @Transactional
    public void on(CommentPostedEvent e) {
        var mentions = MentionParser.extract(e.body());
        if (mentions.isEmpty()) {
            return;
        }
        String title = qa.findQuestion(e.questionId())
                .map(QuestionRef::title)
                .orElse("问题 #" + e.questionId());
        String commenterName = name(e.authorId());
        for (String mention : mentions) {
            Optional<UserSummary> mentioned = identity.findByDisplayName(mention);
            if (mentioned.isEmpty() || mentioned.get().id() == e.authorId()) {
                continue;
            }
            notify(mentioned.get().id(), NotificationType.MENTIONED,
                    commenterName + " 在「" + title + "」的评论中提到了你",
                    link(e.questionId()), e.eventId(), e.occurredAt());
        }
    }

    @EventListener
    @Transactional
    public void on(BountyAwardedEvent e) {
        if (e.winnerId() == e.sponsorId()) {
            return;
        }
        notify(e.winnerId(), NotificationType.BOUNTY_AWARDED,
                "你获得了 " + e.points() + " 分悬赏", link(e.questionId()), e.eventId(), e.occurredAt());
    }

    @EventListener
    @Transactional
    public void on(BadgeAwardedEvent e) {
        notify(e.userId(), NotificationType.BADGE_AWARDED,
                "获得新徽章：" + e.badgeName(), "/me/badges", e.eventId(), e.occurredAt());
    }

    private void notify(long recipientId, NotificationType type, String title, String link, String eventId,
                        Instant at) {
        // INV-N4：同一事件对同一接收者只落一条
        if (notifications.exists(eventId, recipientId)) {
            return;
        }
        notifications.save(Notification.create(recipientId, type, title, link, eventId, at));
    }

    private String name(long userId) {
        return identity.findUser(userId).map(UserSummary::displayName).orElse("某位用户");
    }

    private static String link(long questionId) {
        return "/questions/" + questionId;
    }
}
