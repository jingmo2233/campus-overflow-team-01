package com.campusoverflow.qa.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.api.event.QuestionEditedEvent;
import com.campusoverflow.qa.api.event.QuestionPostedEvent;
import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingCache;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingAudit;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingEvents;
import com.campusoverflow.qa.application.fake.QaTestFixtures.RecordingTags;
import com.campusoverflow.qa.application.fake.QaTestFixtures.SimpleRenderer;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubIdentity;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.Tags;
import com.campusoverflow.qa.domain.Title;
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
 * 发帖 / 编辑 / 删除问题的用例测试。
 * <p>关注「应用服务编排了什么」：课程是否存在的前置校验、标签计数只按差集调整、缓存失效、
 * 审计与集成事件是否成对出现——领域规则本身由 QuestionTest / QaReviewRegressionTest 覆盖。
 */
class QuestionCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private static final Long COURSE_ID = 100L;
    private static final String TITLE = "红黑树插入后为什么必须旋转？";
    private static final String BODY = "变色为什么不够？希望有直观解释，最好能给出一个反例，谢谢。";

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryQuestions questions = new InMemoryQuestions();
    private final RecordingTags tags = new RecordingTags();
    private final RecordingEvents events = new RecordingEvents();
    private final CountingCache cache = new CountingCache();
    private final RecordingAudit audit = new RecordingAudit();
    private final SimpleRenderer renderer = new SimpleRenderer();
    private final StubIdentity identity = new StubIdentity()
            .withUser(1, "小明", Role.STUDENT)
            .withUser(2, "小红", Role.STUDENT)
            .withUser(9, "王老师", Role.TEACHER)
            .withCourseManager(9);

    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor hong = new Actor(2, "小红", Role.STUDENT);
    private final Actor wang = new Actor(9, "王老师", Role.TEACHER);

    private QuestionCommandService service;

    @BeforeEach
    void setUp() {
        service = new QuestionCommandService(questions, tags, identity, events, renderer, cache, audit, clock);
    }

    @Test
    @DisplayName("发布问题：写入聚合、标签计数 +1、摘要交给渲染器截断、缓存失效并留审计")
    void postWiresEveryCollaboratorOnce() {
        long id = service.post(ming, new PostQuestionCommand(COURSE_ID, TITLE, BODY,
                List.of("数据结构", "红黑树")));

        assertThat(questions.findById(id)).isPresent();
        assertThat(tags.usage).containsEntry("数据结构", 1).containsEntry("红黑树", 1);
        // 索引用的纯文本摘要由渲染器负责截断，应用层只负责告诉它截多长。
        assertThat(renderer.plainTextLimits).containsExactly(QuestionCommandService.PLAIN_TEXT_FOR_INDEX);
        assertThat(cache.invalidations).isEqualTo(1);
        assertThat(audit.actions).containsExactly("QUESTION_POSTED");

        List<QuestionPostedEvent> posted = events.ofType(QuestionPostedEvent.class);
        assertThat(posted).hasSize(1);
        assertThat(posted.get(0).questionId()).isEqualTo(id);
        assertThat(posted.get(0).authorId()).isEqualTo(ming.userId());
        assertThat(posted.get(0).courseId()).isEqualTo(COURSE_ID);
    }

    @Test
    @DisplayName("发布问题：课程不存在时以 QA-4003 拒绝，且不落库、不发事件")
    void postRejectsUnknownCourse() {
        identity.withoutCourse(COURSE_ID);

        assertThatThrownBy(() -> service.post(ming,
                new PostQuestionCommand(COURSE_ID, TITLE, BODY, List.of("数据结构"))))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("课程不存在");

        assertThat(events.published).isEmpty();
        assertThat(cache.invalidations).isZero();
    }

    @Test
    @DisplayName("发布问题：不填课程（courseId 为 null）时跳过课程校验，允许发到公共区")
    void postWithoutCourseSkipsCourseCheck() {
        long id = service.post(ming, new PostQuestionCommand(null, TITLE, BODY, List.of("数据结构")));

        assertThat(questions.findById(id)).get().extracting(Question::courseId).isNull();
    }

    @Test
    @DisplayName("发布问题：正文不足 20 字（问题下限高于回答下限）由聚合挡下")
    void postRejectsTooShortBody() {
        assertThatThrownBy(() -> service.post(ming,
                new PostQuestionCommand(COURSE_ID, TITLE, "太短了", List.of("数据结构"))))
                .isInstanceOf(com.campusoverflow.shared.domain.BusinessRuleException.class);

        assertThat(events.published).isEmpty();
    }

    @Test
    @DisplayName("编辑问题：标签计数只按差集增减，避免「改一次热度翻倍」")
    void editAdjustsTagUsageByDifferenceOnly() {
        long id = service.post(ming, new PostQuestionCommand(COURSE_ID, TITLE, BODY,
                List.of("数据结构", "红黑树")));
        tags.usage.clear();

        service.edit(ming, id, new EditQuestionCommand("红黑树插入后为什么必须旋转（修订）", BODY,
                List.of("红黑树", "面试题")));

        // 保留的「红黑树」不动，避免重复计数；新增 +1、移除 -1。
        assertThat(tags.usage).containsOnlyKeys("面试题", "数据结构");
        assertThat(tags.usage).containsEntry("面试题", 1).containsEntry("数据结构", -1);

        List<QuestionEditedEvent> edited = events.ofType(QuestionEditedEvent.class);
        assertThat(edited).hasSize(1);
        assertThat(edited.get(0).tags()).containsExactly("红黑树", "面试题");
        assertThat(audit.actions).contains("QUESTION_EDITED");
    }

    @Test
    @DisplayName("编辑问题：非作者且非课程管理者被拒绝，聚合状态不变")
    void editByStrangerIsForbidden() {
        long id = service.post(ming, new PostQuestionCommand(COURSE_ID, TITLE, BODY, List.of("数据结构")));

        // 标题必须够 10 字才走得到鉴权，否则会先被 Title 的值校验挡下，测不到「越权」这条。
        assertThatThrownBy(() -> service.edit(hong, id,
                new EditQuestionCommand("小红想把标题改成这个", BODY, List.of("数据结构"))))
                .isInstanceOf(com.campusoverflow.shared.domain.ForbiddenException.class);

        assertThat(questions.findById(id)).get().extracting(q -> q.title().value()).isEqualTo(TITLE);
    }

    @Test
    @DisplayName("编辑问题：课程教师可代改，权限来自 Identity 的课程成员关系而非全局角色")
    void editByCourseTeacherIsAllowed() {
        long id = service.post(ming, new PostQuestionCommand(COURSE_ID, TITLE, BODY, List.of("数据结构")));

        service.edit(wang, id, new EditQuestionCommand("王老师帮忙规范了标题", BODY, List.of("数据结构")));

        assertThat(questions.findById(id)).get()
                .extracting(q -> q.title().value()).isEqualTo("王老师帮忙规范了标题");
    }

    @Test
    @DisplayName("编辑不存在的问题：QA-4001")
    void editUnknownQuestion() {
        assertThatThrownBy(() -> service.edit(ming, 404L,
                new EditQuestionCommand(TITLE, BODY, List.of("数据结构"))))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("问题不存在");
    }

    @Test
    @DisplayName("删除问题：软删除、标签计数 -1、发 ContentDeleted 事件供下游清理索引")
    void deleteSoftDeletesAndPublishesEvent() {
        long id = service.post(ming, new PostQuestionCommand(COURSE_ID, TITLE, BODY, List.of("数据结构")));
        tags.usage.clear();

        service.delete(ming, id);

        assertThat(questions.findById(id)).get().extracting(Question::isDeleted).isEqualTo(true);
        assertThat(tags.usage).containsEntry("数据结构", -1);
        assertThat(cache.invalidations).isEqualTo(2); // 发布一次 + 删除一次

        List<ContentDeletedEvent> deleted = events.ofType(ContentDeletedEvent.class);
        assertThat(deleted).hasSize(1);
        assertThat(deleted.get(0).targetType()).isEqualTo("QUESTION");
        assertThat(deleted.get(0).targetId()).isEqualTo(id);
        assertThat(audit.actions).containsExactly("QUESTION_POSTED", "QUESTION_DELETED");
    }

    @Test
    @DisplayName("删除问题：已有回答时提问者本人不能删，课程管理者可以")
    void authorCannotDeleteAnsweredQuestion() {
        Question q = questions.save(Question.post(ming.userId(), COURSE_ID, new Title(TITLE),
                MarkdownBody.ofQuestion(BODY), Tags.of(List.of("数据结构")), NOW));
        // 用 reconstitute 造一个已经带回答数的问题：real 仓储的计数由 SQL 维护，这里直接给聚合。
        Question answered = Question.reconstitute(q.id(), ming.userId(), COURSE_ID, new Title(TITLE),
                MarkdownBody.ofQuestion(BODY), Tags.of(List.of("数据结构")), null, 0, 1, 0, NOW, NOW, null, 1);
        questions.save(answered);

        assertThatThrownBy(() -> service.delete(ming, q.id()))
                .isInstanceOf(com.campusoverflow.shared.domain.BusinessRuleException.class)
                .hasMessageContaining("已有回答");

        service.delete(wang, q.id());
        assertThat(questions.findById(q.id())).get().extracting(Question::isDeleted).isEqualTo(true);
    }

    @Test
    @DisplayName("删除问题：重复删除被当作业务错误，不会发第二次事件")
    void deleteTwiceIsRejected() {
        long id = service.post(ming, new PostQuestionCommand(COURSE_ID, TITLE, BODY, List.of("数据结构")));
        service.delete(ming, id);

        assertThatThrownBy(() -> service.delete(ming, id))
                .isInstanceOf(com.campusoverflow.shared.domain.BusinessRuleException.class);

        assertThat(events.ofType(ContentDeletedEvent.class)).hasSize(1);
    }
}
