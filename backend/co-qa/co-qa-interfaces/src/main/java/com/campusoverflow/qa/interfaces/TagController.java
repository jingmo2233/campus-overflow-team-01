package com.campusoverflow.qa.interfaces;

import com.campusoverflow.qa.application.query.QuestionFilter;
import com.campusoverflow.qa.application.query.QuestionQueryService;
import com.campusoverflow.qa.application.query.QuestionSummaryView;
import com.campusoverflow.qa.application.query.TagView;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "标签 Tag")
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final QuestionQueryService queries;

    public TagController(QuestionQueryService queries) {
        this.queries = queries;
    }

    @Operation(summary = "热门标签")
    @GetMapping("/popular")
    public List<TagView> popular(@RequestParam(defaultValue = "20") int limit) {
        return queries.popularTags(limit);
    }

    /**
     * 标签下的问题列表。标签不存在时返回空页而不是 404：
     * 标签由用户手输，空结果比错误页更符合浏览场景。
     */
    @Operation(summary = "某标签下的问题列表（支持按最新、得分、回答数排序）")
    @GetMapping("/{tag}/questions")
    public PageResult<QuestionSummaryView> questions(@PathVariable String tag,
                                                     @RequestParam(defaultValue = "newest") String sort,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        QuestionFilter filter = QuestionFilter.of(null, tag, QuestionFilter.Sort.parse(sort), false);
        return queries.list(filter, new PageRequest(page, size));
    }
}
