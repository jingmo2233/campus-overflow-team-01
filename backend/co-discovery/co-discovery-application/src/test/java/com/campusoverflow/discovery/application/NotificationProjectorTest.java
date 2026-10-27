package com.campusoverflow.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.discovery.application.fake.DiscoveryTestFixtures;
import com.campusoverflow.discovery.application.fake.DiscoveryTestFixtures.InMemoryNotifications;
import com.campusoverflow.discovery.application.fake.DiscoveryTestFixtures.StubIdentity;
import com.campusoverflow.discovery.application.fake.DiscoveryTestFixtures.StubQa;
import com.campusoverflow.discovery.domain.notification.NotificationType;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.CommentPostedEvent;
import com.campusoverflow.reputation.api.event.BadgeAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationProjectorTest {

    private static final Instant T = DiscoveryTestFixtures.at("2026-10-27T10:00:00Z");

    private InMemoryNotifications notifications;
    private StubIdentity identity;
    private StubQa qa;
    private NotificationProjector projector;

    @BeforeEach
    void setUp() {
        notifications = new InMemoryNotifications();
        identity = new StubIdentity()
                .with(DiscoveryTestFixtures.user(7L, "提问的小张", Role.STUDENT))
                .with(DiscoveryTestFixtures.user(9L, "回答的小李", Role.STUDENT))
                .with(DiscoveryTestFixtures.user(3L, "王老师", Role.TEACHER))
                .with(DiscoveryTestFixtures.user(4L, "小明", Role.STUDENT));
        qa = new StubQa().with(new QuestionRef(11L, 7L, 1L, "红黑树怎么旋转", null, false));
        projector = new NotificationProjector(notifications, identity, qa);
    }

    @Test
    @DisplayName("有人回答我的问题：通知问题作者，标题带答主昵称与问题名")
    void answerSubmittedNotifiesQuestionAuthor() {
        projector.on(new AnswerSubmittedEvent("e1", T, 5L, 11L, 9L, 7L, "红黑树怎么旋转"));

        assertThat(notifications.only().recipientId()).isEqualTo(7L);
        assertThat(notifications.only().type()).isEqualTo(NotificationType.ANSWER_RECEIVED);
        assertThat(notifications.only().title()).contains("回答的小李").contains("红黑树怎么旋转");
        assertThat(notifications.only().link()).isEqualTo("/questions/11");
    }

    @Test
    @DisplayName("INV-N2：自己回答自己的问题，不给自己发通知")
    void selfAnswerProducesNoNotification() {
        projector.on(new AnswerSubmittedEvent("e2", T, 5L, 11L, 7L, 7L, "红黑树怎么旋转"));

        assertThat(notifications.saved).isEmpty();
    }

    @Test
    @DisplayName("回答被采纳：只通知答主，且不通知采纳人自己")
    void acceptedNotifiesAnswerAuthorOnly() {
        projector.on(new AnswerAcceptedEvent("e3", T, 5L, 11L, 9L, 7L, 7L, "红黑树怎么旋转"));

        assertThat(notifications.only().recipientId()).isEqualTo(9L);
        assertThat(notifications.only().type()).isEqualTo(NotificationType.ANSWER_ACCEPTED);
    }

    @Test
    @DisplayName("教师认证：通知答主，标题带认证人昵称")
    void endorsedNotifiesAnswerAuthor() {
        projector.on(new AnswerEndorsedEvent("e4", T, 5L, 11L, 9L, 3L, "红黑树怎么旋转"));

        assertThat(notifications.only().recipientId()).isEqualTo(9L);
        assertThat(notifications.only().type()).isEqualTo(NotificationType.ANSWER_ENDORSED);
        assertThat(notifications.only().title()).contains("王老师");
    }

    @Test
    @DisplayName("评论里 @ 到的人各收一条，标题带上被评论的问题名")
    void mentionsNotifyEachMentionedUser() {
        projector.on(new CommentPostedEvent("e5", T, 2L, 11L, 9L, "感谢 @王老师，另外 @小明 你看看"));

        assertThat(notifications.saved).hasSize(2);
        assertThat(notifications.saved).extracting(n -> n.recipientId()).containsExactly(3L, 4L);
        assertThat(notifications.saved).allMatch(n -> n.type() == NotificationType.MENTIONED);
        assertThat(notifications.saved.get(0).title()).contains("红黑树怎么旋转");
    }

    @Test
    @DisplayName("INV-N2：@ 到自己不算提及")
    void mentionOfSelfIsSkipped() {
        projector.on(new CommentPostedEvent("e6", T, 2L, 11L, 4L, "@小明 自己顶一下"));

        assertThat(notifications.saved).isEmpty();
    }

    @Test
    @DisplayName("悬赏结算：通知赢家，带积分")
    void bountyAwardedNotifiesWinner() {
        projector.on(new BountyAwardedEvent("e7", T, 1L, 11L, 7L, 9L, 50));

        assertThat(notifications.only().recipientId()).isEqualTo(9L);
        assertThat(notifications.only().type()).isEqualTo(NotificationType.BOUNTY_AWARDED);
        assertThat(notifications.only().title()).contains("50");
    }

    @Test
    @DisplayName("徽章授予：通知本人，走徽章页链接")
    void badgeAwardedNotifiesUser() {
        projector.on(new BadgeAwardedEvent("e8", T, 9L, "FIRST_ANSWER", "首答"));

        assertThat(notifications.only().recipientId()).isEqualTo(9L);
        assertThat(notifications.only().type()).isEqualTo(NotificationType.BADGE_AWARDED);
        assertThat(notifications.only().link()).isEqualTo("/me/badges");
    }

    @Test
    @DisplayName("INV-N4：同一事件重复投递，同一接收者只有一条通知")
    void duplicateEventCreatesSingleNotification() {
        AnswerSubmittedEvent e = new AnswerSubmittedEvent("e9", T, 5L, 11L, 9L, 7L, "红黑树怎么旋转");
        projector.on(e);
        projector.on(e);

        assertThat(notifications.saved).hasSize(1);
    }
}
