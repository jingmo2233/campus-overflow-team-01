package com.campusoverflow.qa.application.command;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.api.event.QuestionEditedEvent;
import com.campusoverflow.qa.api.event.QuestionPostedEvent;
import com.campusoverflow.qa.application.port.ContentRenderer;
import com.campusoverflow.qa.application.port.QuestionListCache;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.QuestionRepository;
import com.campusoverflow.qa.domain.TagRepository;
import com.campusoverflow.qa.domain.Tags;
import com.campusoverflow.qa.domain.Title;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：发布 / 编辑 / 删除问题。 */
@Service
public class QuestionCommandService {

    static final int PLAIN_TEXT_FOR_INDEX = 2000;

    private final QuestionRepository questions;
    private final TagRepository tags;
    private final IdentityApi identity;
    private final IntegrationEventPublisher events;
    private final ContentRenderer renderer;
    private final QuestionListCache listCache;
    private final AuditTrail audit;
    private final Clock clock;

    public QuestionCommandService(QuestionRepository questions, TagRepository tags, IdentityApi identity,
                                  IntegrationEventPublisher events, ContentRenderer renderer,
                                  QuestionListCache listCache, AuditTrail audit, Clock clock) {
        this.questions = questions;
        this.tags = tags;
        this.identity = identity;
        this.events = events;
        this.renderer = renderer;
        this.listCache = listCache;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public long post(Actor actor, PostQuestionCommand cmd) {
        ensureCourseExists(cmd.courseId());
        Question question = Question.post(actor.userId(), cmd.courseId(), new Title(cmd.title()),
                MarkdownBody.ofQuestion(cmd.body()), Tags.of(cmd.tags()), clock.instant());
        Question saved = questions.save(question);
        List<String> tagNames = saved.tags().names();
        tags.adjustUsage(tagNames, 1);
        // Outbox 事件与聚合写入同一事务：索引与通知最终一致，但绝不丢事件（ADR-002）。
        events.publish(new QuestionPostedEvent(EventIds.next(), clock.instant(), saved.id(), saved.authorId(),
                saved.courseId(), saved.title().value(), plainText(saved.body().value()), tagNames));
        listCache.invalidateAll();
        audit.record("QUESTION_POSTED", actor.userId(), "QUESTION", String.valueOf(saved.id()),
                saved.title().value());
        return saved.id();
    }

    @Transactional
    public void edit(Actor actor, long questionId, EditQuestionCommand cmd) {
        Question question = load(questionId);
        List<String> before = question.tags().names();
        question.edit(actor, managesCourse(actor, question), new Title(cmd.title()),
                MarkdownBody.ofQuestion(cmd.body()), Tags.of(cmd.tags()), clock.instant());
        questions.save(question);

        // 只按差集调整使用计数，避免「编辑一次热度翻倍」。
        List<String> after = question.tags().names();
        Set<String> added = new HashSet<>(after);
        added.removeAll(before);
        Set<String> removed = new HashSet<>(before);
        removed.removeAll(after);
        if (!added.isEmpty()) {
            tags.adjustUsage(added, 1);
        }
        if (!removed.isEmpty()) {
            tags.adjustUsage(removed, -1);
        }

        events.publish(new QuestionEditedEvent(EventIds.next(), clock.instant(), questionId,
                question.title().value(), plainText(question.body().value()), after));
        listCache.invalidateAll();
        audit.record("QUESTION_EDITED", actor.userId(), "QUESTION", String.valueOf(questionId),
                question.title().value());
    }

    @Transactional
    public void delete(Actor actor, long questionId) {
        Question question = load(questionId);
        question.softDelete(actor, managesCourse(actor, question), clock.instant());
        questions.save(question);
        tags.adjustUsage(question.tags().names(), -1);
        events.publish(new ContentDeletedEvent(EventIds.next(), clock.instant(), "QUESTION", questionId, questionId));
        listCache.invalidateAll();
        audit.record("QUESTION_DELETED", actor.userId(), "QUESTION", String.valueOf(questionId),
                question.title().value());
    }

    private Question load(long questionId) {
        return questions.findById(questionId)
                .orElseThrow(() -> new NotFoundException("QA-4001", "问题不存在"));
    }

    /**
     * 「是否管理该问题所属课程」由 Identity 的课程成员关系决定，不能用 {@code Actor.isTeacherOrAdmin()} 代替：
     * 一位不教这门课的教师对本问题没有管理权（见 docs/错误码.md「关于课程管理者」）。
     */
    private boolean managesCourse(Actor actor, Question question) {
        return identity.canManageCourse(actor.userId(), question.courseId());
    }

    private void ensureCourseExists(Long courseId) {
        if (courseId != null && identity.findCourse(courseId).isEmpty()) {
            throw new NotFoundException("QA-4003", "课程不存在");
        }
    }

    private String plainText(String markdown) {
        return renderer.toPlainText(markdown, PLAIN_TEXT_FOR_INDEX);
    }
}
