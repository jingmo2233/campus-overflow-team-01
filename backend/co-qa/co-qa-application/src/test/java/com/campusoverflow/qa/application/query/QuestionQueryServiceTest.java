package com.campusoverflow.qa.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingCache;
import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingViews;
import com.campusoverflow.qa.application.fake.QaTestFixtures.SimpleRenderer;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubIdentity;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubReadModel;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 查询用例测试。读路径的坑集中在三处：缓存命中后不能再打库、我的投票必须按登录态区分、
 * 回答排序要把「被采纳」优先于「高分」。这三条都不是领域规则，只能在应用层测出来。
 */
class QuestionQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T06:00:00Z");
    private static final Long COURSE_ID = 100L;

    private final StubReadModel readModel = new StubReadModel();
    private final CountingCache cache = new CountingCache();
    private final CountingViews views = new CountingViews();
    private final SimpleRenderer renderer = new SimpleRenderer();
    private final StubIdentity identity = new StubIdentity()
            .withUser(1, "小明", Role.STUDENT)
            .withUser(2, "小红", Role.STUDENT)
            .withUser(9, "王老师", Role.TEACHER)
            .withCourseManager(9);

    private final Actor ming = new Actor(1, "小明", Role.STUDENT);
    private final Actor hong = new Actor(2, "小红", Role.STUDENT);
    private final Actor wang = new Actor(9, "王老师", Role.TEACHER);

    private QuestionQueryService service;

    @BeforeEach
    void setUp() {
        service = new QuestionQueryService(readModel, identity, renderer, views, cache);
    }

    @Test
    @DisplayName("列表：投影成摘要视图，作者信息批量补齐，并把结果写回缓存")
    void listProjectsRowsIntoSummaryViews() {
        readModel.total = 42;
        readModel.questions.add(new QuestionRow(10, 1, COURSE_ID, "红黑树插入后为什么必须旋转？",
                "变色为什么不够？希望有直观解释。", List.of("数据结构", "红黑树"), 7L, 5, 3, 88, NOW, NOW));

        PageResult<QuestionSummaryView> page = service.list(COURSE_ID, new PageRequest(1, 20));

        assertThat(page.items()).hasSize(1);
        QuestionSummaryView view = page.items().get(0);
        assertThat(view.id()).isEqualTo(10);
        assertThat(view.author().displayName()).isEqualTo("小明");
        assertThat(view.accepted()).isTrue();      // acceptedAnswerId != null
        assertThat(view.score()).isEqualTo(5);
        assertThat(view.answerCount()).isEqualTo(3);
        assertThat(page.total()).isEqualTo(42);    // total 取读模型的真实总数，不是本页条数
        assertThat(cache.puts).isEqualTo(1);
    }

    @Test
    @DisplayName("列表：缓存命中直接返回，不再查库")
    void listServesFromCache() {
        readModel.questions.add(new QuestionRow(10, 1, COURSE_ID, "红黑树插入后为什么必须旋转？", "正文正文正文正文正文",
                List.of("数据结构"), null, 0, 0, 0, NOW, NOW));
        PageResult<QuestionSummaryView> first = service.list(COURSE_ID, new PageRequest(1, 20));

        readModel.questions.clear(); // 若缓存失效，这次会读到空列表
        PageResult<QuestionSummaryView> second = service.list(COURSE_ID, new PageRequest(1, 20));

        assertThat(second).isEqualTo(first);
        assertThat(cache.puts).isEqualTo(1);
    }

    @Test
    @DisplayName("列表：作者已注销时摘要仍要能显示，不抛空指针")
    void listToleratesUnknownAuthor() {
        readModel.questions.add(new QuestionRow(10, 777, COURSE_ID, "红黑树插入后为什么必须旋转？", "正文正文正文正文正文",
                List.of("数据结构"), null, 0, 0, 0, NOW, NOW));

        QuestionSummaryView view = service.list(COURSE_ID, new PageRequest(1, 20)).items().get(0);

        assertThat(view.author().displayName()).isEqualTo("已注销用户");
        assertThat(view.author().verified()).isFalse();
    }

    @Test
    @DisplayName("列表：摘要长度交给渲染器按 160 字截断")
    void listUsesExcerptLength() {
        readModel.questions.add(new QuestionRow(10, 1, COURSE_ID, "红黑树插入后为什么必须旋转？", "正文正文正文正文正文",
                List.of("数据结构"), null, 0, 0, 0, NOW, NOW));

        service.list(COURSE_ID, new PageRequest(1, 20));

        assertThat(renderer.plainTextLimits).containsExactly(160);
    }

    @Test
    @DisplayName("详情：浏览计数先落 Redis，属于读路径的唯一副作用")
    void detailIncrementsViewCounter() {
        givenQuestionWithAnswers(0, null);

        service.detail(10L, null);

        assertThat(views.counts).containsEntry(10L, 1);
    }

    @Test
    @DisplayName("详情：回答排序为「被采纳 → 高分 → 更早」，与数据库返回顺序无关")
    void detailSortsAnswersByAcceptedThenScoreThenTime() {
        // 采纳的是 22 号（低分但被采纳），用来验证「采纳优先于分数」而不是「分数优先」。
        givenQuestionWithAnswers(1, 22L);
        readModel.answers.clear();
        readModel.answers.add(answer(20, 2, "低分回答", 1, null, NOW.plusSeconds(30)));
        readModel.answers.add(answer(21, 2, "高分但未采纳", 9, null, NOW.plusSeconds(60)));
        readModel.answers.add(answer(22, 2, "低分但被采纳", 3, null, NOW.plusSeconds(90)));

        QuestionDetailView detail = service.detail(10L, null);

        assertThat(detail.answers()).extracting(AnswerView::id).containsExactly(22L, 21L, 20L);
        assertThat(detail.answers()).filteredOn(AnswerView::accepted).singleElement()
                .extracting(AnswerView::id).isEqualTo(22L);
    }

    @Test
    @DisplayName("详情：未登录时不查我的投票，myVote 一律为 0")
    void detailWithoutActorSkipsVoteLookup() {
        givenQuestionWithAnswers(0, null);
        readModel.myVotes.put("QUESTION:10", 1);

        QuestionDetailView detail = service.detail(10L, null);

        assertThat(detail.myVote()).isZero();
        assertThat(detail.permissions()).isEqualTo(PermissionsView.NONE);
        assertThat(readModel.myVotesQueriedFor).isEmpty();
    }

    @Test
    @DisplayName("详情：登录后按 key 取出问题与各回答上的我的投票")
    void detailReturnsMyVotesWhenLoggedIn() {
        givenQuestionWithAnswers(1, null);
        readModel.myVotes.put("QUESTION:10", 1);
        readModel.myVotes.put("ANSWER:20", -1);

        QuestionDetailView detail = service.detail(10L, ming);

        assertThat(detail.myVote()).isEqualTo(1);
        AnswerView answerView = detail.answers().stream().filter(a -> a.id() == 20L).findFirst().orElseThrow();
        assertThat(answerView.myVote()).isEqualTo(-1);
        assertThat(readModel.myVotesQueriedFor).containsExactly(ming.userId());
    }

    @Test
    @DisplayName("详情：无回答时作者本人可删；有回答后本人只剩「可编辑」")
    void permissionsReflectAnswerCount() {
        givenQuestionWithAnswers(0, null);
        assertThat(service.detail(10L, ming).permissions())
                .isEqualTo(new PermissionsView(true, true, true, false));

        givenQuestionWithAnswers(2, null);
        assertThat(service.detail(10L, ming).permissions())
                .isEqualTo(new PermissionsView(true, false, true, false));
    }

    @Test
    @DisplayName("详情：已采纳后提问者仍可编辑，但不能再采纳")
    void permissionsAfterAccept() {
        givenQuestionWithAnswers(1, 20L);

        PermissionsView permissions = service.detail(10L, ming).permissions();

        assertThat(permissions.canEdit()).isTrue();
        assertThat(permissions.canAccept()).isFalse();
        assertThat(permissions.canManage()).isFalse();
    }

    @Test
    @DisplayName("详情：与提问无关的普通学生没有任何操作权限")
    void strangerHasNoPermissions() {
        givenQuestionWithAnswers(0, null);

        assertThat(service.detail(10L, hong).permissions()).isEqualTo(PermissionsView.NONE);
    }

    @Test
    @DisplayName("详情：课程管理者可删任意问题，并带上 canManage 标记")
    void courseManagerCanManage() {
        givenQuestionWithAnswers(1, null);

        PermissionsView permissions = service.detail(10L, wang).permissions();

        assertThat(permissions.canManage()).isTrue();
        assertThat(permissions.canDelete()).isTrue();
        assertThat(permissions.canEdit()).isTrue();
    }

    @Test
    @DisplayName("详情：问题不存在时 QA-4001，且不记浏览")
    void detailOfUnknownQuestion() {
        assertThatThrownBy(() -> service.detail(404L, ming))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("问题不存在");

        assertThat(views.counts).isEmpty();
    }

    @Test
    @DisplayName("详情：评论按所属目标分流，问题评论不会混进回答评论")
    void detailSplitsCommentsByTarget() {
        givenQuestionWithAnswers(1, null);
        readModel.comments.add(new CommentRow(100, "QUESTION", 10L, 2L, null, "同问，蹲一个答案", NOW));
        readModel.comments.add(new CommentRow(101, "ANSWER", 20L, 1L, null, "这个解释很清楚", NOW));

        QuestionDetailView detail = service.detail(10L, null);

        assertThat(detail.comments()).extracting(CommentView::id).containsExactly(100L);
        AnswerView answerView = detail.answers().stream().filter(a -> a.id() == 20L).findFirst().orElseThrow();
        assertThat(answerView.comments()).extracting(CommentView::id).containsExactly(101L);
    }

    @Test
    @DisplayName("详情：没有课程的问题不暴露课程信息，不抛异常")
    void detailWithoutCourse() {
        readModel.questions.add(new QuestionRow(10, 1, null, "红黑树插入后为什么必须旋转？", "正文正文正文正文正文",
                List.of("数据结构"), null, 0, 0, 0, NOW, NOW));

        assertThat(service.detail(10L, null).course()).isNull();
    }

    @Test
    @DisplayName("热门标签：limit 被夹到 [1, 50]，防止一次拉全表")
    void popularTagsClampsLimit() {
        readModel.tags.addAll(List.of(new TagView("数据结构", 12), new TagView("红黑树", 8)));

        assertThat(service.popularTags(0)).hasSize(1);
        assertThat(service.popularTags(999)).hasSize(2);
    }

    /** 造一个问答现场：作者是小明（1），回答者在 2 号，按需设置回答数与采纳。 */
    private void givenQuestionWithAnswers(int answerCount, Long acceptedAnswerId) {
        readModel.questions.clear();
        readModel.answers.clear();
        readModel.comments.clear();
        readModel.questions.add(new QuestionRow(10, ming.userId(), COURSE_ID, "红黑树插入后为什么必须旋转？",
                "变色为什么不够？希望有直观解释。", List.of("数据结构"), acceptedAnswerId, 0, answerCount, 88, NOW, NOW));
        if (answerCount > 0) {
            readModel.answers.add(answer(20, hong.userId(), "关键在于黑高这个不变量。", 1, null, NOW));
        }
    }

    private static AnswerRow answer(long id, long authorId, String body, int score, Long endorsedBy, Instant at) {
        return new AnswerRow(id, authorId, body, score, endorsedBy, at, at);
    }
}
