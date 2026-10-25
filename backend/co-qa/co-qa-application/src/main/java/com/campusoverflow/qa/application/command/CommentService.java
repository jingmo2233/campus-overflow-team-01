package com.campusoverflow.qa.application.command;

import com.campusoverflow.qa.api.event.CommentPostedEvent;
import com.campusoverflow.qa.domain.Answer;
import com.campusoverflow.qa.domain.AnswerRepository;
import com.campusoverflow.qa.domain.Comment;
import com.campusoverflow.qa.domain.CommentRepository;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.QuestionRepository;
import com.campusoverflow.qa.domain.TargetType;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.event.EventIds;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.security.Actor;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：评论与二级回复。评论中的 @昵称 由 Discovery 解析并发送提醒。 */
@Service
public class CommentService {

    private final CommentRepository comments;
    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public CommentService(CommentRepository comments, QuestionRepository questions, AnswerRepository answers,
                          IntegrationEventPublisher events, Clock clock) {
        this.comments = comments;
        this.questions = questions;
        this.answers = answers;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public long post(Actor actor, PostCommentCommand cmd) {
        long questionId = resolveQuestionId(cmd.targetType(), cmd.targetId());
        Comment parent = cmd.parentId() == null ? null : comments.findById(cmd.parentId())
                .orElseThrow(() -> new NotFoundException("QA-4004", "被回复的评论不存在"));
        // 一层回复限制、父评论归属校验、正文长度全部由 Comment.post 守护。
        Comment saved = comments.save(Comment.post(cmd.targetType(), cmd.targetId(), questionId, actor.userId(),
                parent, cmd.body(), clock.instant()));
        // 正文随事件带给 Discovery：@昵称 的解析与提醒不属于 Q&A 的职责。
        events.publish(new CommentPostedEvent(EventIds.next(), clock.instant(), saved.id(), questionId,
                saved.authorId(), saved.body()));
        return saved.id();
    }

    /** 评论必须落在一个仍然开放的问题下；同时解析出「评论挂在哪个问题」这一冗余字段供读模型使用。 */
    private long resolveQuestionId(TargetType targetType, long targetId) {
        return switch (targetType) {
            case QUESTION -> loadOpenQuestion(targetId).id();
            case ANSWER -> {
                Answer answer = answers.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("QA-4002", "回答不存在"));
                if (answer.isDeleted()) {
                    throw new BusinessRuleException("QA-1008", "回答已删除，不能再评论");
                }
                yield loadOpenQuestion(answer.questionId()).id();
            }
        };
    }

    private Question loadOpenQuestion(long id) {
        Question question = questions.findById(id)
                .orElseThrow(() -> new NotFoundException("QA-4001", "问题不存在"));
        question.ensureOpenForAnswers();
        return question;
    }
}
