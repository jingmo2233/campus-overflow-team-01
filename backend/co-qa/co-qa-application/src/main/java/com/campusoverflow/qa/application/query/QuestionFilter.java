package com.campusoverflow.qa.application.query;

import com.campusoverflow.qa.domain.TagName;

/**
 * 问题列表的筛选条件。字段为 {@code null} 表示不限制。
 *
 * <p>标签在这里就归一化（小写、去掉前导 #、校验字符集），这样后续的读模型与缓存键不会因为
 * {@code C++} 与 {@code c++} 被当成两个不同的筛选条件。</p>
 */
public record QuestionFilter(Long courseId, String tag, Sort sort, boolean unanswered) {

    /** 列表排序方式（语义层，SQL 片段由读模型适配器决定）。 */
    public enum Sort {
        /** 最新发布优先（默认）。 */
        NEWEST,
        /** 得分高优先。 */
        VOTES,
        /** 回答多优先。 */
        ANSWERS;

        public static Sort parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return NEWEST;
            }
            String normalized = raw.strip();
            for (Sort candidate : values()) {
                if (candidate.name().equalsIgnoreCase(normalized)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("不支持的排序方式：" + raw);
        }
    }

    /** 无筛选条件：等同首页默认列表。 */
    public static final QuestionFilter ALL = new QuestionFilter(null, null, Sort.NEWEST, false);

    public QuestionFilter {
        tag = normalizeTag(tag);
        sort = sort == null ? Sort.NEWEST : sort;
    }

    public static QuestionFilter of(Long courseId, String tag, Sort sort, boolean unanswered) {
        return new QuestionFilter(courseId, tag, sort, unanswered);
    }

    /** 某门课程下的列表。 */
    public QuestionFilter withCourse(Long courseId) {
        return new QuestionFilter(courseId, tag, sort, unanswered);
    }

    /** 缓存键片段：保证不同筛选组合不会互相串用缓存。 */
    public String cacheKey() {
        return (courseId == null ? "all" : courseId) + ":"
                + (tag == null ? "all" : tag) + ":"
                + sort.name().toLowerCase(java.util.Locale.ROOT) + ":"
                + (unanswered ? "unanswered" : "any");
    }

    private static String normalizeTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        // 复用领域层的校验：统一小写、去掉前导 #、非法字符抛 QA-1004。
        return new TagName(tag).value();
    }
}
