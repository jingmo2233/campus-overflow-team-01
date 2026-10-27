package com.campusoverflow.discovery.application;

import com.campusoverflow.discovery.domain.search.RelatedQuestionPolicy;
import com.campusoverflow.discovery.domain.search.SearchHit;
import com.campusoverflow.discovery.domain.search.SearchIndex;
import com.campusoverflow.discovery.domain.search.SearchPort;
import com.campusoverflow.discovery.domain.search.SearchQuery;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.shared.page.PageResult;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：搜索与筛选、相关问题推荐。 */
@Service
@Transactional(readOnly = true)
public class DiscoveryQueryService {

    /** 拉取候选池后再在内存里按 Jaccard 精确排序，避免把排序规则下推到 SQL 适配器。 */
    private static final int CANDIDATE_POOL = 50;

    private final SearchPort search;
    private final SearchIndex index;
    private final IdentityApi identity;

    public DiscoveryQueryService(SearchPort search, SearchIndex index, IdentityApi identity) {
        this.search = search;
        this.index = index;
        this.identity = identity;
    }

    public PageResult<SearchResultView> search(SearchQuery query) {
        PageResult<SearchHit> hits = search.search(query);
        Map<Long, UserSummary> authors = hits.items().isEmpty()
                ? Map.of()
                : identity.findUsers(hits.items().stream().map(SearchHit::authorId).distinct().toList());
        return hits.map(hit -> toView(hit, authors.get(hit.authorId())));
    }

    public List<RelatedQuestionView> related(long questionId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> tags = index.findTags(questionId);
        if (tags.isEmpty()) {
            return List.of();
        }
        List<SearchIndex.RelatedCandidate> candidates =
                index.findCandidatesSharingTags(questionId, tags, CANDIDATE_POOL);
        int ownTagCount = tags.size();
        return RelatedQuestionPolicy.rank(candidates, ownTagCount, limit).stream()
                .map(candidate -> new RelatedQuestionView(candidate.questionId(), candidate.title(),
                        candidate.answerCount(), candidate.accepted(),
                        RelatedQuestionPolicy.jaccard(candidate.sharedTags(), ownTagCount, candidate.tagCount())))
                .toList();
    }

    private static SearchResultView toView(SearchHit hit, UserSummary author) {
        SearchResultView.Author view = author == null
                ? new SearchResultView.Author(hit.authorId(), "已注销用户", null, false)
                : new SearchResultView.Author(author.id(), author.displayName(), author.role().name(),
                        author.verified());
        return new SearchResultView(hit.questionId(), hit.title(), hit.snippet(), hit.tags(), hit.courseId(),
                view, hit.score(), hit.answerCount(), hit.accepted(), hit.bountyOpen(), hit.createdAt());
    }
}
