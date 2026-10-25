package com.campusoverflow.qa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ForbiddenException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 评审 P1-2 / P2-2 回归：错误码语义可区分、鉴权先于归属校验。
 */
class QaReviewRegressionTest {

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");

    @Test
    void commentDepthAndWrongTargetUseDifferentCodes() {
        Comment top = Comment.post(TargetType.ANSWER, 50, 10, 1, null, "请问第二步为什么要变色？", NOW);
        top.assignId(1);
        Comment reply = Comment.post(TargetType.ANSWER, 50, 10, 3, top, "因为要保持黑高一致。", NOW);
        reply.assignId(2);

        assertThatThrownBy(() -> Comment.post(TargetType.ANSWER, 50, 10, 1, reply, "再回复一层", NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.code()).isEqualTo("QA-1007"));

        assertThatThrownBy(() -> Comment.post(TargetType.QUESTION, 10, 10, 1, top, "挂错位置的回复", NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.code()).isEqualTo("QA-1012"));
    }

    @Test
    void unauthorizedUserCannotProbeAnswerOwnership() {
        Question question = QuestionTest.persistedQuestion();
        Answer foreign = Answer.reconstitute(60L, 999L, 3,
                MarkdownBody.ofAnswer("这是另一个问题下面的回答内容"), 0, null, NOW, NOW, null, 0);

        // 非提问者、非课程管理者：无论回答属不属于这个问题，都应先得到 403 而不是「不属于本问题」
        assertThatThrownBy(() -> question.accept(foreign, QuestionTest.OTHER, false))
                .isInstanceOfSatisfying(ForbiddenException.class,
                        e -> assertThat(e.code()).isEqualTo("QA-3001"));
    }

    @Test
    void ownerStillGetsOwnershipErrorForForeignAnswer() {
        Question question = QuestionTest.persistedQuestion();
        Answer foreign = Answer.reconstitute(60L, 999L, 3,
                MarkdownBody.ofAnswer("这是另一个问题下面的回答内容"), 0, null, NOW, NOW, null, 0);

        assertThatThrownBy(() -> question.accept(foreign, QuestionTest.ASKER, false))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> assertThat(e.code()).isEqualTo("QA-1009"));
    }
}
