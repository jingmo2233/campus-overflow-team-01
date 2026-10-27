package com.campusoverflow.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.discovery.application.fake.DiscoveryTestFixtures;
import com.campusoverflow.discovery.application.fake.DiscoveryTestFixtures.InMemoryProcessed;
import com.campusoverflow.discovery.application.fake.DiscoveryTestFixtures.RecordingSearchIndex;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.api.event.QuestionEditedEvent;
import com.campusoverflow.qa.api.event.QuestionPostedEvent;
import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.reputation.api.event.BountyAwardedEvent;
import com.campusoverflow.reputation.api.event.BountyClosedEvent;
import com.campusoverflow.reputation.api.event.BountyOpenedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchIndexProjectorTest {

    private static final Instant T = DiscoveryTestFixtures.at("2026-10-27T10:00:00Z");

    private RecordingSearchIndex index;
    private InMemoryProcessed processed;
    private SearchIndexProjector projector;

    @BeforeEach
    void setUp() {
        index = new RecordingSearchIndex();
        processed = new InMemoryProcessed();
        projector = new SearchIndexProjector(index, processed);
    }

    @Test
    @DisplayName("发布问题：整条文档落进投影，标签与课程一并带上")
    void questionPostedInsertsFullDocument() {
        projector.on(new QuestionPostedEvent("e1", T, 11L, 7L, 3L, "红黑树怎么旋转", "正文", List.of("java", "算法")));

        assertThat(index.inserts).hasSize(1);
        var doc = index.inserts.get(0);
        assertThat(doc.questionId()).isEqualTo(11L);
        assertThat(doc.courseId()).isEqualTo(3L);
        assertThat(doc.authorId()).isEqualTo(7L);
        assertThat(doc.title()).isEqualTo("红黑树怎么旋转");
        assertThat(doc.tags()).containsExactly("java", "算法");
        assertThat(doc.createdAt()).isEqualTo(T);
    }

    @Test
    @DisplayName("编辑问题：只更新文本与标签，不动计数")
    void questionEditedUpdatesTextOnly() {
        projector.on(new QuestionEditedEvent("e2", T, 11L, "新标题", "新正文", List.of("树")));

        assertThat(index.textUpdates).containsExactly("11|新标题|新正文|树");
        assertThat(index.answerDeltas).isEmpty();
        assertThat(index.scoreDeltas).isEmpty();
    }

    @Test
    @DisplayName("回答提交：回答数 +1")
    void answerSubmittedIncrementsCount() {
        projector.on(new AnswerSubmittedEvent("e3", T, 5L, 11L, 9L, 7L, "红黑树怎么旋转"));

        assertThat(index.answerDeltas).containsExactly("11:1");
    }

    @Test
    @DisplayName("删问题软删整条投影；删回答只减计数")
    void deleteSemanticsDifferByTargetType() {
        projector.on(new ContentDeletedEvent("e4", T, "QUESTION", 11L, 11L));
        projector.on(new ContentDeletedEvent("e5", T, "ANSWER", 5L, 11L));

        assertThat(index.deleted).containsExactly(11L);
        assertThat(index.answerDeltas).containsExactly("11:-1");
    }

    @Test
    @DisplayName("采纳：标记 accepted 并刷新活跃时间")
    void acceptedMarksFlag() {
        projector.on(new AnswerAcceptedEvent("e6", T, 5L, 11L, 9L, 7L, 7L, "红黑树怎么旋转"));

        assertThat(index.accepted).containsExactly(11L);
    }

    @Test
    @DisplayName("投票：用 newValue-oldValue 作增量，改投与撤销都能正确表达")
    void voteUsesDifferenceOfValues() {
        projector.on(new VoteCastEvent("e7", T, "QUESTION", 11L, 7L, 3L, 11L, 1, -1));
        projector.on(new VoteCastEvent("e8", T, "ANSWER", 5L, 9L, 4L, 11L, 0, 1));

        assertThat(index.scoreDeltas).containsExactly("11:-2", "11:1");
    }

    @Test
    @DisplayName("票数没变（0 → 0）不写库，省一次无意义 UPDATE")
    void zeroDeltaSkipsWrite() {
        projector.on(new VoteCastEvent("e9", T, "QUESTION", 11L, 7L, 3L, 11L, 0, 0));

        assertThat(index.scoreDeltas).isEmpty();
    }

    @Test
    @DisplayName("悬赏开与关：开启置 true，获奖与到期都置 false")
    void bountyFlagsFollowLifecycle() {
        projector.on(new BountyOpenedEvent("e10", T, 1L, 11L, 7L, 50, T.plus(Duration.ofDays(7))));
        projector.on(new BountyAwardedEvent("e11", T, 1L, 11L, 7L, 9L, 50));
        projector.on(new BountyClosedEvent("e12", T, 1L, 11L, 7L, 50));

        assertThat(index.bountyFlags).containsExactly("11:true", "11:false", "11:false");
    }

    @Test
    @DisplayName("同一 eventId 重复投递只投影一次（消费者幂等）")
    void duplicateEventIsProjectedOnce() {
        AnswerSubmittedEvent e = new AnswerSubmittedEvent("e13", T, 5L, 11L, 9L, 7L, "红黑树怎么旋转");
        projector.on(e);
        projector.on(e);
        projector.on(e);

        assertThat(index.answerDeltas).containsExactly("11:1");
        assertThat(processed.size()).isEqualTo(1);
    }
}
