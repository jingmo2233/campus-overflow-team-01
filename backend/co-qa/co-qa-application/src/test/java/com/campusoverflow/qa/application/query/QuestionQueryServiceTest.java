package com.campusoverflow.qa.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.qa.application.fake.QaTestFixtures.StubIdentity;
import com.campusoverflow.qa.application.port.ContentRenderer;
import com.campusoverflow.qa.application.port.QaReadModel;
import com.campusoverflow.qa.application.port.QuestionListCache;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 列表查询用例：验证筛选条件传递、缓存分维度隔离与视图组装。 */
class QuestionQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T02:00:00Z");

    private final RecordingReadModel readModel = new RecordingReadModel();
    private final StubIdentity identity = new StubIdentity().withUser(1, "小明", Role.STUDENT);
    private final MapCache cache = new MapCache();
    private final QuestionQueryService service = new QuestionQueryService(readModel, identity,
            new PlainRenderer(), questionId -> { }, cache);

    @Test
    @DisplayName("筛选条件原样下推到读模型，标签统一小写归一化")
    void filterIsPushedDownAndTagNormalized() {
        readModel.page = pageOf(questionRow(7, 1, List.of("c++")));

        service.list(QuestionFilter.of(100L, "C++", QuestionFilter.Sort.VOTES, true), new PageRequest(2, 10));

        QuestionFilter used = readModel.lastFilter;
        assertThat(used.courseId()).isEqualTo(100L);
        assertThat(used.tag()).isEqualTo("c++");
        assertThat(used.sort()).isEqualTo(QuestionFilter.Sort.VOTES);
        assertThat(used.unanswered()).isTrue();
        assertThat(readModel.lastPage).isEqualTo(new PageRequest(2, 10));
    }

    @Test
    @DisplayName("同一筛选条件命中缓存后不再回源")
    void secondIdenticalQueryHitsCache() {
        readModel.page = pageOf(questionRow(7, 1, List.of("java")));
        QuestionFilter filter = QuestionFilter.of(null, "java", QuestionFilter.Sort.NEWEST, false);

        service.list(filter, new PageRequest(1, 20));
        service.list(filter, new PageRequest(1, 20));

        assertThat(readModel.calls).isEqualTo(1);
        assertThat(cache.puts).isEqualTo(1);
    }

    @Test
    @DisplayName("不同筛选组合使用不同缓存键，不会串数据")
    void differentFiltersDoNotShareCacheEntries() {
        readModel.page = pageOf(questionRow(7, 1, List.of("java")));

        service.list(QuestionFilter.ALL, new PageRequest(1, 20));
        service.list(QuestionFilter.of(null, "java", QuestionFilter.Sort.NEWEST, false), new PageRequest(1, 20));
        service.list(QuestionFilter.of(null, null, QuestionFilter.Sort.VOTES, false), new PageRequest(1, 20));
        service.list(QuestionFilter.of(null, null, QuestionFilter.Sort.NEWEST, true), new PageRequest(1, 20));

        assertThat(readModel.calls).isEqualTo(4);
        assertThat(cache.puts).isEqualTo(4);
    }

    @Test
    @DisplayName("列表视图带作者名与纯文本摘要，作品信息来自批量查询")
    void listViewCarriesAuthorAndExcerpt() {
        readModel.page = pageOf(questionRow(7, 1, List.of("数据结构", "红黑树")));

        PageResult<QuestionSummaryView> result = service.list(QuestionFilter.ALL, new PageRequest(1, 20));

        QuestionSummaryView view = result.items().get(0);
        assertThat(view.id()).isEqualTo(7);
        assertThat(view.author().displayName()).isEqualTo("小明");
        assertThat(view.tags()).containsExactly("数据结构", "红黑树");
        assertThat(view.excerpt()).isEqualTo("正文摘要");
        assertThat(view.accepted()).isFalse();
    }

    @Test
    @DisplayName("标签不存在时返回空页，不抛 404")
    void unknownTagYieldsEmptyPage() {
        readModel.page = PageResult.empty(1, 20);

        PageResult<QuestionSummaryView> result = service.list(
                QuestionFilter.of(null, "不存在的标签", null, false), new PageRequest(1, 20));

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    @DisplayName("排序参数非法时给出明确错误")
    void illegalSortIsRejected() {
        assertThatThrownBy(() -> QuestionFilter.Sort.parse("hot"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的排序方式");
    }

    @Test
    @DisplayName("热门标签的数量参数被夹紧到合法区间")
    void popularTagLimitClamped() {
        service.popularTags(0);
        assertThat(readModel.lastTagLimit).isEqualTo(1);

        service.popularTags(999);
        assertThat(readModel.lastTagLimit).isEqualTo(PageRequest.MAX_SIZE);
    }

    private static PageResult<QuestionRow> pageOf(QuestionRow row) {
        return new PageResult<>(List.of(row), 1, 1, 20);
    }

    private static QuestionRow questionRow(long id, long authorId, List<String> tags) {
        return new QuestionRow(id, authorId, 100L, "标题" + id, "正文摘要", tags, null, 0, 0, 0, NOW, NOW);
    }

    /** 记录读模型收到的筛选条件；同时验证「应用层不做内存过滤」。 */
    private static final class RecordingReadModel implements QaReadModel {
        private PageResult<QuestionRow> page = PageResult.empty(1, 20);
        private QuestionFilter lastFilter;
        private PageRequest lastPage;
        private int lastTagLimit;
        private int calls;

        @Override
        public PageResult<QuestionRow> listQuestions(QuestionFilter filter, PageRequest page) {
            this.lastFilter = filter;
            this.lastPage = page;
            this.calls++;
            return this.page;
        }

        @Override
        public Optional<QuestionRow> findQuestion(long questionId) {
            return page.items().stream().filter(row -> row.id() == questionId).findFirst();
        }

        @Override
        public List<AnswerRow> findAnswers(long questionId) {
            return List.of();
        }

        @Override
        public List<CommentRow> findComments(long questionId) {
            return List.of();
        }

        @Override
        public Map<String, Integer> findMyVotes(long voterId, long questionId, Collection<Long> answerIds) {
            return Map.of();
        }

        @Override
        public List<TagView> popularTags(int limit) {
            this.lastTagLimit = limit;
            return List.of();
        }
    }

    /** 按「筛选键 + 页码」存结果的简易缓存，用于验证键的隔离性。 */
    private static final class MapCache implements QuestionListCache {
        private final Map<String, PageResult<QuestionSummaryView>> store = new LinkedHashMap<>();
        private int puts;

        @Override
        public Optional<PageResult<QuestionSummaryView>> get(QuestionFilter filter, PageRequest page) {
            return Optional.ofNullable(store.get(key(filter, page)));
        }

        @Override
        public void put(QuestionFilter filter, PageRequest page, PageResult<QuestionSummaryView> value) {
            puts++;
            store.put(key(filter, page), value);
        }

        @Override
        public void invalidateAll() {
            store.clear();
        }

        private static String key(QuestionFilter filter, PageRequest page) {
            return filter.cacheKey() + ":" + page.page() + ":" + page.size();
        }
    }

    private static final class PlainRenderer implements ContentRenderer {
        @Override
        public String toSafeHtml(String markdown) {
            return markdown;
        }

        @Override
        public String toPlainText(String markdown, int maxLength) {
            return markdown;
        }
    }
}
