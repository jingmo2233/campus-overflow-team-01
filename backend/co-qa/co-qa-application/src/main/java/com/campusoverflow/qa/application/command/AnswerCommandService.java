package com.campusoverflow.qa.application.command;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.AnswerEndorsedEvent;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.application.port.QuestionListCache;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.AnswerRepository;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.QuestionRepository;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：提交 / 编辑 / 删除回答，采纳最佳答案，教师认证。 */
@Service
public class AnswerCommandService {

    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final IdentityApi identity;
    private final IntegrationEventPublisher events;
    private final QuestionListCache listCache;
    private final AuditTrail audit;
    private final Clock clock;

    public AnswerCommandService(QuestionRepository questions, AnswerRepository answers, IdentityApi identity,
                                IntegrationEventPublisher events, QuestionListCache listCache, AuditTrail audit,
                                Clock clock) {
        this.questions = questions;
        this.answers = answers;
        this.identity = identity;
        this.events = events;
        this.listCache = listCache;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public long submit(Actor actor, long questionId, String body) {
        Question question = loadQuestion(questionId);
        // Answer.submit 内部会调用 question.ensureOpenForAnswers()，已删除的问题在这里被挡下。
        Answer saved = answers.save(Answer.submit(question, actor.userId(), MarkdownBody.ofAnswer(body),
                clock.instant()));
        // 计数器走原子 UPDATE 而不是「读-改-写」聚合，避免热门问题上的并发丢更新。
        questions.adjustAnswerCount(questionId, 1);
        events.publish(new AnswerSubmittedEvent(EventIds.next(), clock.instant(), saved.id(), questionId,
                saved.authorId(), question.authorId(), question.title().value()));
        listCache.invalidateAll();
        audit.record("ANSWER_SUBMITTED", actor.userId(), "ANSWER", String.valueOf(saved.id()),
                "question=" + questionId);
        return saved.id();
    }

    @Transactional
    public void edit(Actor actor, long answerId, String body) {
        Answer answer = loadAnswer(answerId);
        Question question = loadQuestion(answer.questionId());
        answer.edit(actor, managesCourse(actor, question), MarkdownBody.ofAnswer(body), clock.instant());
        answers.save(answer);
        listCache.invalidateAll();
        audit.record("ANSWER_EDITED", actor.userId(), "ANSWER", String.valueOf(answerId),
                "question=" + answer.questionId());
    }

    @Transactional
    public void delete(Actor actor, long answerId) {
        Answer answer = loadAnswer(answerId);
        Question question = loadQuestion(answer.questionId());
        // 「是否已被采纳」的唯一事实来源是 Question.acceptedAnswerId，由应用层查出后传给聚合。
        answer.softDelete(actor, managesCourse(actor, question), question.isAccepted(answerId), clock.instant());
        answers.save(answer);
        questions.adjustAnswerCount(answer.questionId(), -1);
        events.publish(new ContentDeletedEvent(EventIds.next(), clock.instant(), "ANSWER", answerId,
                answer.questionId()));
        listCache.invalidateAll();
        audit.record("ANSWER_DELETED", actor.userId(), "ANSWER", String.valueOf(answerId),
                "question=" + answer.questionId());
    }

    /** 场景 R-2：采纳最佳答案。状态变更与 Outbox 事件在同一事务提交；声誉结算异步进行。 */
    @Transactional
    public void accept(Actor actor, long questionId, long answerId) {
        Question question = loadQuestion(questionId);
        Answer answer = loadAnswer(answerId);
        // 鉴权、归属校验、重复采纳判定全部在 Question.accept 内完成（顺序见该方法的注释）。
        question.accept(answer, actor, managesCourse(actor, question));
        questions.save(question);
        events.publish(new AnswerAcceptedEvent(EventIds.next(), clock.instant(), answerId, questionId,
                answer.authorId(), question.authorId(), actor.userId(), question.title().value()));
        listCache.invalidateAll();
        audit.record("ANSWER_ACCEPTED", actor.userId(), "ANSWER", String.valueOf(answerId),
                "question=" + questionId);
    }

    @Transactional
    public void endorse(Actor actor, long answerId) {
        Answer answer = loadAnswer(answerId);
        Question question = loadQuestion(answer.questionId());
        answer.endorse(actor, managesCourse(actor, question));
        answers.save(answer);
        events.publish(new AnswerEndorsedEvent(EventIds.next(), clock.instant(), answerId, question.id(),
                answer.authorId(), actor.userId(), question.title().value()));
        listCache.invalidateAll();
        audit.record("ANSWER_ENDORSED", actor.userId(), "ANSWER", String.valueOf(answerId),
                "question=" + answer.questionId());
    }

    private boolean managesCourse(Actor actor, Question question) {
        return identity.canManageCourse(actor.userId(), question.courseId());
    }

    private Question loadQuestion(long id) {
        return questions.findById(id).orElseThrow(() -> new NotFoundException("QA-4001", "问题不存在"));
    }

    private Answer loadAnswer(long id) {
        return answers.findById(id).orElseThrow(() -> new NotFoundException("QA-4002", "回答不存在"));
    }
}
