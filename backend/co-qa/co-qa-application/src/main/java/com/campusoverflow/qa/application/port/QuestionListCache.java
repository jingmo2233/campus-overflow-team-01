package com.campusoverflow.qa.application.port;

import com.campusoverflow.qa.application.query.QuestionFilter;
import com.campusoverflow.qa.application.query.QuestionSummaryView;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import java.util.Optional;

/** 问题列表缓存端口（Cache-Aside，TTL 60s + 抖动；写操作后整体失效）。 */
public interface QuestionListCache {
    Optional<PageResult<QuestionSummaryView>> get(QuestionFilter filter, PageRequest page);

    void put(QuestionFilter filter, PageRequest page, PageResult<QuestionSummaryView> value);

    void invalidateAll();
}
