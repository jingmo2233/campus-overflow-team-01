package com.campusoverflow.discovery.domain.search;

import java.util.Comparator;
import java.util.List;

/**
 * 相关问题推荐（规则引擎，非 AI）：按标签集合的 Jaccard 相似度排序，
 * J(A,B) = |A∩B| / |A∪B|；相似度相同则得分高、已采纳者优先。
 */
public final class RelatedQuestionPolicy {

    private RelatedQuestionPolicy() {
    }

    public static double jaccard(int shared, int sizeA, int sizeB) {
        int union = sizeA + sizeB - shared;
        if (shared <= 0 || union <= 0) {
            return 0d;
        }
        return (double) shared / union;
    }

    public static List<SearchIndex.RelatedCandidate> rank(List<SearchIndex.RelatedCandidate> candidates, int ownTagCount,
                                                          int limit) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }
        return candidates.stream()
                .filter(candidate -> candidate.sharedTags() > 0)
                .sorted(Comparator
                        .comparingDouble((SearchIndex.RelatedCandidate candidate) ->
                                jaccard(candidate.sharedTags(), ownTagCount, candidate.tagCount()))
                        .reversed()
                        .thenComparing(Comparator.comparingInt(SearchIndex.RelatedCandidate::score).reversed())
                        .thenComparing(candidate -> !candidate.accepted())
                        .thenComparingLong(SearchIndex.RelatedCandidate::questionId))
                .limit(limit)
                .toList();
    }
}
