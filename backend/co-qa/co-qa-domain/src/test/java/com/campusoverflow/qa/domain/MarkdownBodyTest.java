package com.campusoverflow.qa.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.shared.domain.BusinessRuleException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 评审 P1-1 回归：record 的规范构造器是 public 的，不能成为绕过工厂校验的后门。
 * 这里直接攻击构造器，锁定「任何 MarkdownBody 实例都至少满足公共下限」这一不变式。
 */
class MarkdownBodyTest {

    private static final Instant NOW = Instant.parse("2026-10-01T08:00:00Z");

    @Test
    void canonicalConstructorRejectsEmptyBlankNullAndOversizedBody() {
        assertThatThrownBy(() -> new MarkdownBody("")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new MarkdownBody("   ")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new MarkdownBody(null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new MarkdownBody("x".repeat(30001))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void questionBodyHasStricterFloorThanAnswerBody() {
        assertThatThrownBy(() -> MarkdownBody.ofQuestion("x".repeat(19))).isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> MarkdownBody.ofQuestion("x".repeat(20))).doesNotThrowAnyException();
        assertThatThrownBy(() -> MarkdownBody.ofAnswer("x".repeat(9))).isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> MarkdownBody.ofAnswer("x".repeat(10))).doesNotThrowAnyException();
    }

    @Test
    void canonicalConstructorCannotSmuggleShortBodyIntoQuestion() {
        // 15 字是合法的回答正文，可以绕过工厂直接 new 出来
        MarkdownBody fifteen = new MarkdownBody("x".repeat(15));

        // 但它进入 Question 聚合时必须被拦下：问题正文下限 20，不能靠调用方自觉
        assertThatThrownBy(() -> Question.post(1, null, new Title("红黑树插入后如何判断旋转方向？"),
                fifteen, Tags.of(List.of("java")), NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("问题正文");

        Question persisted = QuestionTest.persistedQuestion();
        assertThatThrownBy(() -> persisted.edit(QuestionTest.ASKER, false, persisted.title(), fifteen,
                persisted.tags(), NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("问题正文");
    }

    @Test
    void bodyIsTrimmedAndPreservedAsText() {
        MarkdownBody body = MarkdownBody.ofAnswer("   缩进会被去掉，但内部内容保留   ");
        assertThat(body.value()).isEqualTo("缩进会被去掉，但内部内容保留");
    }
}
