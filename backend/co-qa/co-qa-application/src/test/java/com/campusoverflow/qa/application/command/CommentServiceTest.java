package com.campusoverflow.qa.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.api.event.CommentPostedEvent;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryAnswers;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryComments;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingEvents;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.Comment;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.Tags;
import com.campusoverflow.qa.domain.TargetType;
import com.campusoverflow.qa.domain.Title;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 评论与二级回复的用例测试。
 * <p>这一层的实质职责是「解析出评论挂在哪个问题」：评论表要冗余 questionId 才能支撑读模型，
 * 而这条链路在评论回答时多跳一次（回答 → 问题），最容易漏测。
 */
class CommentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private static final String TITLE = "红黑树插入后为什么必须旋转？";
    private static final String BODY = "变色为什么不够？希望有直观解释，最好能给出一个反例，谢谢。";

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryComments comments = new InMemoryComments();
    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final InMemoryAnswers answers = new InMemoryAnswers();
    private final RecordingEvents events = new RecordingEvents();

    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor hong = new Actor(2, "小红", Role.STUDENT);
    private final Actor longge = new Actor(3, "阿龙", Role.TA);

    private CommentService service;
    private long questionId;
    private long answerId;

    @BeforeEach
    void setUp() {
        service = new CommentService(comments, questions, answers, events, clock);
        Question question = questions.save(Question.post(ming.userId(), 100L, new Title(TITLE),
                MarkdownBody.ofQuestion(BODY), Tags.of(List.of("数据结构")), NOW));
        questionId = question.id();
        answerId = answers.save(Answer.submit(question, hong.userId(),
                MarkdownBody.ofAnswer("关键在于黑高这个不变量，仅靠变色无法调整。"), NOW)).id();
    }

    @Test
    @DisplayName("评论问题：冗余的 questionId 直接取被评论的问题")
    void commentOnQuestionCarriesQuestionId() {
        long id = service.post(hong, new PostCommentCommand(TargetType.QUESTION, questionId, null, "同问，蹲一个答案"));

        Comment saved = comments.get(id);
        assertThat(saved.questionId()).isEqualTo(questionId);
        assertThat(saved.targetType()).isEqualTo(TargetType.QUESTION);
        assertThat(saved.targetId()).isEqualTo(questionId);
        assertThat(saved.parentId()).isNull();
        assertThat(saved.authorId()).isEqualTo(hong.userId());

        List<CommentPostedEvent> posted = events.ofType(CommentPostedEvent.class);
        assertThat(posted).hasSize(1);
        assertThat(posted.get(0).commentId()).isEqualTo(id);
        assertThat(posted.get(0).questionId()).isEqualTo(questionId);
        assertThat(posted.get(0).body()).isEqualTo("同问，蹲一个答案");
    }

    @Test
    @DisplayName("评论回答：questionId 要多跳一层，从回答反查回它的所属问题")
    void commentOnAnswerResolvesQuestionThroughAnswer() {
        long id = service.post(longge, new PostCommentCommand(TargetType.ANSWER, answerId, null, "这个解释很清楚"));

        Comment saved = comments.get(id);
        assertThat(saved.targetType()).isEqualTo(TargetType.ANSWER);
        assertThat(saved.targetId()).isEqualTo(answerId);
        assertThat(saved.questionId()).isEqualTo(questionId); // 关键：不是 answerId
    }

    @Test
    @DisplayName("二级回复：parentId 落库，但 questionId 仍指向问题")
    void replyKeepsParentIdAndQuestionId() {
        long parent = service.post(longge, new PostCommentCommand(TargetType.ANSWER, answerId, null, "这个解释很清楚"));
        long reply = service.post(hong, new PostCommentCommand(TargetType.ANSWER, answerId, parent, "补充一句：黑高相等即可"));

        Comment saved = comments.get(reply);
        assertThat(saved.parentId()).isEqualTo(parent);
        assertThat(saved.questionId()).isEqualTo(questionId);
    }

    @Test
    @DisplayName("二级回复只支持一层：回复一个已经是回复的评论被拒绝")
    void replyToReplyIsRejected() {
        long parent = service.post(longge, new PostCommentCommand(TargetType.ANSWER, answerId, null, "这个解释很清楚"));
        long reply = service.post(hong, new PostCommentCommand(TargetType.ANSWER, answerId, parent, "补充一句：黑高相等即可"));

        assertThatThrownBy(() -> service.post(ming,
                new PostCommentCommand(TargetType.ANSWER, answerId, reply, "再回一层")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("只支持一层");
    }

    @Test
    @DisplayName("父评论不存在时以 QA-4004 拒绝")
    void unknownParentIsRejected() {
        assertThatThrownBy(() -> service.post(hong,
                new PostCommentCommand(TargetType.QUESTION, questionId, 999L, "回复一个不存在的评论")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("被回复的评论不存在");

        assertThat(events.published).isEmpty();
    }

    @Test
    @DisplayName("回复必须与被回复评论挂在同一内容下，否则 QA-1012")
    void replyMustStayOnSameTarget() {
        long parentOnQuestion = service.post(hong,
                new PostCommentCommand(TargetType.QUESTION, questionId, null, "同问，蹲一个答案"));

        assertThatThrownBy(() -> service.post(longge,
                new PostCommentCommand(TargetType.ANSWER, answerId, parentOnQuestion, "跨内容回复")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("同一内容");
    }

    @Test
    @DisplayName("问题已删除：不能再评论（QA-1008），评论不会落库")
    void cannotCommentOnDeletedQuestion() {
        Question deleted = Question.reconstitute(questionId, ming.userId(), 100L, new Title(TITLE),
                MarkdownBody.ofQuestion(BODY), Tags.of(List.of("数据结构")), null, 0, 0, 0,
                NOW, NOW, NOW, 1);
        questions.save(deleted);

        assertThatThrownBy(() -> service.post(hong,
                new PostCommentCommand(TargetType.QUESTION, questionId, null, "来晚了")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("问题已删除");
    }

    @Test
    @DisplayName("回答已删除：不能再评论（QA-1008）")
    void cannotCommentOnDeletedAnswer() {
        Answer deleted = Answer.reconstitute(answerId, questionId, hong.userId(),
                MarkdownBody.ofAnswer("关键在于黑高这个不变量，仅靠变色无法调整。"), 0, null,
                NOW, NOW, NOW, 1);
        answers.save(deleted);

        assertThatThrownBy(() -> service.post(longge,
                new PostCommentCommand(TargetType.ANSWER, answerId, null, "补充")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("回答已删除");
    }

    @Test
    @DisplayName("问题或回答不存在：分别给 QA-4001 / QA-4002")
    void unknownTargetsAreRejected() {
        assertThatThrownBy(() -> service.post(hong,
                new PostCommentCommand(TargetType.QUESTION, 404L, null, "评论不存在的问题")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("问题不存在");

        assertThatThrownBy(() -> service.post(hong,
                new PostCommentCommand(TargetType.ANSWER, 404L, null, "评论不存在的回答")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("回答不存在");
    }

    @Test
    @DisplayName("评论正文不能为空，上限 600 字")
    void commentBodyIsGuarded() {
        assertThatThrownBy(() -> service.post(hong,
                new PostCommentCommand(TargetType.QUESTION, questionId, null, "   ")))
                .isInstanceOf(BusinessRuleException.class);

        String tooLong = "嗯".repeat(Comment.MAX_LENGTH + 1);
        assertThatThrownBy(() -> service.post(hong,
                new PostCommentCommand(TargetType.QUESTION, questionId, null, tooLong)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("评论长度应在");
    }
}
