package com.campusoverflow.qa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AnswerTest {

    private final Actor answerer = new Actor(3, "回答者", Role.STUDENT);
    private final Actor teacher = new Actor(9, "王老师", Role.TEACHER);

    @Test
    void onlyCourseManagerCanEndorseAndOnlyOnce() {
        Question q = QuestionTest.persistedQuestion();
        Answer a = QuestionTest.persistedAnswer(q, answerer.userId(), 50);
        assertThatThrownBy(() -> a.endorse(answerer, false)).isInstanceOf(ForbiddenException.class);
        a.endorse(teacher, true);
        assertThat(a.endorsedBy()).isEqualTo(9L);
        assertThatThrownBy(() -> a.endorse(teacher, true)).isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptedAnswerCannotBeDeletedByItsAuthor() {
        Question q = QuestionTest.persistedQuestion();
        Answer a = QuestionTest.persistedAnswer(q, answerer.userId(), 50);
        assertThatThrownBy(() -> a.softDelete(answerer, false, true, QuestionTest.NOW))
                .isInstanceOf(BusinessRuleException.class);
        a.softDelete(teacher, true, true, QuestionTest.NOW);
        assertThat(a.isDeleted()).isTrue();
    }

    @Test
    void strangersCannotEditOrDelete() {
        Question q = QuestionTest.persistedQuestion();
        Answer a = QuestionTest.persistedAnswer(q, answerer.userId(), 50);
        Actor stranger = new Actor(77, "路人", Role.STUDENT);
        assertThatThrownBy(() -> a.edit(stranger, false, MarkdownBody.ofAnswer("这是被路人篡改后的回答内容"), QuestionTest.NOW))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> a.softDelete(stranger, false, false, QuestionTest.NOW))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void authorCanEditOwnAnswerAndUpdatedAtMovesForward() {
        Question q = QuestionTest.persistedQuestion();
        Answer a = QuestionTest.persistedAnswer(q, answerer.userId(), 50);
        Instant later = QuestionTest.NOW.plusSeconds(120);

        a.edit(answerer, false, MarkdownBody.ofAnswer("补充：还要看父节点是不是祖父节点的左子树。"), later);

        assertThat(a.body().value()).contains("祖父节点");
        assertThat(a.updatedAt()).isEqualTo(later);
        assertThat(a.updatedAt()).isAfter(a.createdAt());
    }

    @Test
    void courseManagerCanEditOthersAnswer() {
        Question q = QuestionTest.persistedQuestion();
        Answer a = QuestionTest.persistedAnswer(q, answerer.userId(), 50);

        a.edit(teacher, true, MarkdownBody.ofAnswer("助教订正：叔叔节点为红色时只需变色，无需旋转。"), QuestionTest.NOW);

        assertThat(a.body().value()).contains("助教订正");
    }

    @Test
    void deletedAnswerCannotBeEditedOrEndorsedOrDeletedAgain() {
        Question q = QuestionTest.persistedQuestion();
        Answer a = QuestionTest.persistedAnswer(q, answerer.userId(), 50);
        a.softDelete(answerer, false, false, QuestionTest.NOW);

        assertThatThrownBy(() -> a.edit(answerer, false, MarkdownBody.ofAnswer("已经删掉了还想接着改内容"), QuestionTest.NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> a.endorse(teacher, true)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> a.softDelete(teacher, true, false, QuestionTest.NOW))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void submittedAnswerCarriesQuestionIdAndStartsClean() {
        Question q = QuestionTest.persistedQuestion();
        Answer a = QuestionTest.persistedAnswer(q, answerer.userId(), 50);

        assertThat(a.questionId()).isEqualTo(q.id());
        assertThat(a.authorId()).isEqualTo(answerer.userId());
        assertThat(a.endorsedBy()).isNull();
        assertThat(a.score()).isZero();
        assertThat(a.isDeleted()).isFalse();
        assertThat(a.createdAt()).isEqualTo(QuestionTest.NOW);
        assertThat(a.updatedAt()).isEqualTo(QuestionTest.NOW);
    }
}
