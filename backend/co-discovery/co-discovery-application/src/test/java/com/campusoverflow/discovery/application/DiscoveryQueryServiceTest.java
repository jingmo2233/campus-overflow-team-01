package com.campusoverflow.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.discovery.application.fake.DiscoveryTestFixtures;
import com.campusoverflow.discovery.application.fake.DiscoveryTestFixtures.RecordingSearchIndex;
import com.campusoverflow.discovery.application.fake.DiscoveryTestFixtures.StubIdentity;
import com.campusoverflow.discovery.application.fake.DiscoveryTestFixtures.StubSearchPort;
import com.campusoverflow.discovery.domain.search.SearchHit;
import com.campusoverflow.discovery.domain.search.SearchIndex.RelatedCandidate;
import com.campusoverflow.discovery.domain.search.SearchQuery;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiscoveryQueryServiceTest {

    private static final Instant T = DiscoveryTestFixtures.at("2026-10-27T10:00:00Z");

    private StubSearchPort searchPort;
    private RecordingSearchIndex index;
    private StubIdentity identity;
    private DiscoveryQueryService service;

    @BeforeEach
    void setUp() {
        searchPort = new StubSearchPort();
        index = new RecordingSearchIndex();
        identity = new StubIdentity()
                .with(DiscoveryTestFixtures.user(7L, "提问的小张", Role.STUDENT))
                .with(DiscoveryTestFixtures.user(3L, "王老师", Role.TEACHER));
        service = new DiscoveryQueryService(searchPort, index, identity);
    }

    private static SearchHit hit(long questionId, String title, long authorId) {
        return new SearchHit(questionId, title, "摘要", List.of("java"), 1L, authorId, 3, 2, false, false, T);
    }

    @Test
    @DisplayName("搜索结果映射成视图，作者信息一次批量查完（不做 N+1）")
    void searchMapsHitsAndBatchesAuthors() {
        searchPort.result = new PageResult<>(List.of(hit(11L, "红黑树怎么旋转", 7L), hit(12L, "线段树入门", 7L)),
                2, 1, 20);

        var page = service.search(new SearchQuery("红黑树", null, null, null, false, false, null));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(SearchResultView::title).containsExactly("红黑树怎么旋转", "线段树入门");
        assertThat(page.items().get(0).author().displayName()).isEqualTo("提问的小张");
        assertThat(page.items().get(0).author().role()).isEqualTo("STUDENT");
        assertThat(identity.findUsersCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("作者查不到时降级成占位，不让整页搜索失败")
    void unknownAuthorFallsBack() {
        searchPort.result = new PageResult<>(List.of(hit(11L, "红黑树怎么旋转", 999L)), 1, 1, 20);

        var page = service.search(new SearchQuery("红黑树", null, null, null, false, false, null));

        assertThat(page.items().get(0).author().displayName()).isEqualTo("已注销用户");
        assertThat(page.items().get(0).author().id()).isEqualTo(999L);
    }

    @Test
    @DisplayName("空结果不触发作者查询")
    void emptyResultSkipsAuthorLookup() {
        searchPort.result = PageResult.empty(1, 20);

        service.search(new SearchQuery("红黑树", null, null, null, false, false, null));

        assertThat(identity.findUsersCalls).isZero();
    }

    @Test
    @DisplayName("相关问题按 Jaccard 降序取前 limit，并带回相似度")
    void relatedRanksByJaccard() {
        index.tags = List.of("java", "算法", "树");
        index.candidates = List.of(
                new RelatedCandidate(1L, "A", 1, 5, 99, 3, true),
                new RelatedCandidate(2L, "B", 2, 2, 0, 0, false),
                new RelatedCandidate(3L, "C", 2, 3, 5, 1, true));

        var related = service.related(11L, 2);

        assertThat(related).extracting(RelatedQuestionView::id).containsExactly(2L, 3L);
        assertThat(related.get(0).similarity()).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(related.get(1).similarity()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("候选池按固定上限拉取，排序留在应用层做")
    void relatedPullsFixedCandidatePool() {
        index.tags = List.of("java");

        service.related(11L, 3);

        assertThat(index.lastCandidateLimit).isEqualTo(50);
    }

    @Test
    @DisplayName("问题没有标签（或 limit 非法）时直接返回空，不查候选")
    void relatedWithoutTagsReturnsEmpty() {
        index.tags = List.of();

        assertThat(service.related(11L, 5)).isEmpty();

        index.tags = List.of("java");
        assertThat(service.related(11L, 0)).isEmpty();
    }

    @Test
    @DisplayName("搜索透传原始查询条件，不在应用层改写")
    void searchPassesQueryThrough() {
        SearchQuery query = new SearchQuery("红黑树", 1L, "Java", null, true, false, new PageRequest(2, 10));

        service.search(query);

        assertThat(searchPort.lastQuery).isSameAs(query);
    }
}
