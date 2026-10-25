package com.campusoverflow.qa.application;

import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.qa.domain.QuestionRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class QaQueryApiImpl implements QaQueryApi {

    private final QuestionRepository questions;

    public QaQueryApiImpl(QuestionRepository questions) {
        this.questions = questions;
    }

    /**
     * 已软删除的问题仍然返回，但带上 {@code deleted = true}。下游（Reputation 对账、Discovery 索引清理）
     * 需要据此做补偿——直接返回空会让它们无法区分「问题不存在」与「问题已删除」。
     */
    @Override
    public Optional<QuestionRef> findQuestion(long questionId) {
        return questions.findById(questionId)
                .map(q -> new QuestionRef(q.id(), q.authorId(), q.courseId(), q.title().value(),
                        q.acceptedAnswerId(), q.isDeleted()));
    }
}
