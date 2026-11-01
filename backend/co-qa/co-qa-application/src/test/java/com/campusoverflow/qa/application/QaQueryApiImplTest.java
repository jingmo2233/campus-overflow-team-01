package com.campusoverflow.qa.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryQuestions;
import com.campusoverflow.qa.domain.MarkdownBody;
import com.campusoverflow.qa.domain.Question;
import com.campusoverflow.qa.domain.Tags;
import com.campusoverflow.qa.domain.Title;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 防腐层（发布语言）测试：{@link QaQueryApiImpl} 是其他上下文访问 Q&A 的唯一入口，
 * 一旦它把「已删除」和「不存在」混为一谈，下游的对账与索引清理就会静默出错。
 */
class QaQueryApiImplTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private static final Long COURSE_ID = 100L;
    private static final String TITLE = "红黑树插入后为什么必须旋转？";
    private static final String BODY = "变色为什么不够？希望有直观解释，最好能给出一个反例，谢谢。";

    private final InMemoryQuestions questions = new InMemoryQuestions();
    private QaQueryApiImpl api;

    @BeforeEach
    void setUp() {
        api = new QaQueryApiImpl(questions);
    }

    @Test
    @DisplayName("找到问题：只暴露下游需要的最少字段，不带正文与标签")
    void findQuestionMapsToPublishLanguage() {
        Question question = questions.save(Question.post(1L, COURSE_ID, new Title(TITLE),
                MarkdownBody.ofQuestion(BODY), Tags.of(List.of("数据结构")), NOW));

        Optional<QuestionRef> found = api.findQuestion(question.id());

        assertThat(found).isPresent();
        QuestionRef ref = found.get();
        assertThat(ref.id()).isEqualTo(question.id());
        assertThat(ref.authorId()).isEqualTo(1L);
        assertThat(ref.courseId()).isEqualTo(COURSE_ID);
        assertThat(ref.title()).isEqualTo(TITLE);
        assertThat(ref.acceptedAnswerId()).isNull();
        assertThat(ref.deleted()).isFalse();
    }

    @Test
    @DisplayName("已软删除的问题仍然返回，但带 deleted = true：下游需要据此做补偿")
    void deletedQuestionIsStillReturnedWithFlag() {
        Question deleted = Question.reconstitute(7L, 1L, COURSE_ID, new Title(TITLE),
                MarkdownBody.ofQuestion(BODY), Tags.of(List.of("数据结构")), null, 0, 0, 0,
                NOW, NOW, NOW, 1);
        questions.save(deleted);

        Optional<QuestionRef> found = api.findQuestion(7L);

        assertThat(found).isPresent();
        assertThat(found.get().deleted()).isTrue();
    }

    @Test
    @DisplayName("问题不存在：返回空 Optional，与「已删除」明确区分开")
    void unknownQuestionReturnsEmpty() {
        assertThat(api.findQuestion(404L)).isEmpty();
    }

    @Test
    @DisplayName("采纳状态原样透出，供 Reputation 结算与 Discovery 展示")
    void acceptedAnswerIdIsPassedThrough() {
        Question accepted = Question.reconstitute(8L, 1L, COURSE_ID, new Title(TITLE),
                MarkdownBody.ofQuestion(BODY), Tags.of(List.of("数据结构")), 55L, 0, 1, 0,
                NOW, NOW, null, 2);
        questions.save(accepted);

        assertThat(api.findQuestion(8L)).get().extracting(QuestionRef::acceptedAnswerId).isEqualTo(55L);
    }
}
