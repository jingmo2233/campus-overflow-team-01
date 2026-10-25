package com.campusoverflow.qa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VoteAndCommentTest {

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");

    @Test
    void cannotVoteOnOwnContent() {
        assertThatThrownBy(() -> Vote.cast(3, 3, TargetType.ANSWER, 50, 1, NOW))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("自己");
    }

    @Test
    void voteValueMustBePlusOrMinusOne() {
        assertThatThrownBy(() -> Vote.cast(1, 3, TargetType.ANSWER, 50, 2, NOW))
                .isInstanceOf(BusinessRuleException.class);
        Vote v = Vote.cast(1, 3, TargetType.ANSWER, 50, 1, NOW);
        assertThat(v.change(-1, NOW)).isEqualTo(1);
        assertThat(v.value()).isEqualTo(-1);
    }

    @Test
    void repliesAreLimitedToOneLevelOnSameTarget() {
        Comment top = Comment.post(TargetType.ANSWER, 50, 10, 1, null, "请问第二步为什么要变色？", NOW);
        top.assignId(1);
        Comment reply = Comment.post(TargetType.ANSWER, 50, 10, 3, top, "因为要保持黑高一致。", NOW);
        reply.assignId(2);
        assertThat(reply.parentId()).isEqualTo(1L);

        assertThatThrownBy(() -> Comment.post(TargetType.ANSWER, 50, 10, 1, reply, "再回复一层", NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Comment.post(TargetType.QUESTION, 10, 10, 1, top, "挂错位置的回复", NOW))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectedChangeLeavesVoteUntouched() {
        Vote v = Vote.cast(1, 3, TargetType.ANSWER, 50, 1, NOW);

        assertThatThrownBy(() -> v.change(0, NOW.plusSeconds(60))).isInstanceOf(BusinessRuleException.class);

        assertThat(v.value()).isEqualTo(1);
        assertThat(v.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void changeRecordsNewValueAndTimestamp() {
        Vote v = Vote.cast(1, 3, TargetType.QUESTION, 10, -1, NOW);
        Instant later = NOW.plusSeconds(60);

        assertThat(v.change(1, later)).isEqualTo(-1);

        assertThat(v.value()).isEqualTo(1);
        assertThat(v.updatedAt()).isEqualTo(later);
        assertThat(v.targetType()).isEqualTo(TargetType.QUESTION);
        assertThat(v.targetId()).isEqualTo(10L);
        assertThat(v.voterId()).isEqualTo(1L);
    }

    @Test
    void topLevelCommentHasNoParent() {
        Comment top = Comment.post(TargetType.QUESTION, 10, 10, 7, null, "建议补一张插入后的示意图。", NOW);

        assertThat(top.parentId()).isNull();
        assertThat(top.targetType()).isEqualTo(TargetType.QUESTION);
        assertThat(top.questionId()).isEqualTo(10L);
        assertThat(top.authorId()).isEqualTo(7L);
        assertThat(top.createdAt()).isEqualTo(NOW);
    }

    @Test
    void commentBodyLengthIsEnforced() {
        assertThatThrownBy(() -> Comment.post(TargetType.ANSWER, 50, 10, 1, null, "   ", NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Comment.post(TargetType.ANSWER, 50, 10, 1, null, "超".repeat(601), NOW))
                .isInstanceOf(BusinessRuleException.class);

        Comment atLimit = Comment.post(TargetType.ANSWER, 50, 10, 1, null, "好".repeat(600), NOW);
        assertThat(atLimit.body()).hasSize(600);
    }
}
