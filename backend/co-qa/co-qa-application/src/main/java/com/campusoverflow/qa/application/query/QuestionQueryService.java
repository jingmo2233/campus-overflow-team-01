package com.campusoverflow.qa.application.query;

import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.application.port.ContentRenderer;
import com.campusoverflow.qa.application.port.QaReadModel;
import com.campusoverflow.qa.application.port.QuestionListCache;
import com.campusoverflow.qa.application.port.ViewCounter;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 问答查询用例（读路径）。 */
@Service
@Transactional(readOnly = true)
public class QuestionQueryService {

    private static final int EXCERPT_LENGTH = 160;

    private final QaReadModel readModel;
    private final IdentityApi identity;
    private final ContentRenderer renderer;
    private final ViewCounter viewCounter;
    private final QuestionListCache cache;

    public QuestionQueryService(QaReadModel readModel, IdentityApi identity, ContentRenderer renderer,
                                ViewCounter viewCounter, QuestionListCache cache) {
        this.readModel = readModel;
        this.identity = identity;
        this.renderer = renderer;
        this.viewCounter = viewCounter;
        this.cache = cache;
    }

    /** Cache-Aside：列表不含「我的投票」，与登录态无关，因此可以整页缓存给所有用户复用。 */
    public PageResult<QuestionSummaryView> list(Long courseId, PageRequest page) {
        Optional<PageResult<QuestionSummaryView>> cached = cache.get(courseId, page);
        if (cached.isPresent()) {
            return cached.get();
        }

        PageResult<QuestionRow> rows = readModel.listQuestions(courseId, page);
        Map<Long, UserSummary> authors = identity.findUsers(
                rows.items().stream().map(QuestionRow::authorId).collect(Collectors.toSet()));
        PageResult<QuestionSummaryView> result = rows.map(row -> new QuestionSummaryView(row.id(), row.title(),
                renderer.toPlainText(row.body(), EXCERPT_LENGTH), row.tags(), row.courseId(),
                AuthorView.of(row.authorId(), authors.get(row.authorId())), row.score(), row.answerCount(),
                row.viewCount(), row.acceptedAnswerId() != null, row.createdAt()));
        cache.put(courseId, page, result);
        return result;
    }

    public QuestionDetailView detail(long questionId, Actor actorOrNull) {
        QuestionRow question = readModel.findQuestion(questionId)
                .orElseThrow(() -> new NotFoundException("QA-4001", "问题不存在"));
        // 浏览计数先落 Redis，由定时任务批量回写，避免热点行上的写放大。
        viewCounter.increment(questionId);

        List<AnswerRow> answerRows = readModel.findAnswers(questionId);
        List<CommentRow> commentRows = readModel.findComments(questionId);

        // 提问者、回答者、认证教师、评论者一次批量取回，避免 N+1。
        Set<Long> userIds = new HashSet<>();
        userIds.add(question.authorId());
        answerRows.forEach(row -> {
            userIds.add(row.authorId());
            if (row.endorsedBy() != null) {
                userIds.add(row.endorsedBy());
            }
        });
        commentRows.forEach(row -> userIds.add(row.authorId()));
        Map<Long, UserSummary> users = identity.findUsers(userIds);

        Map<String, Integer> myVotes = actorOrNull == null ? Map.of()
                : readModel.findMyVotes(actorOrNull.userId(), questionId,
                        answerRows.stream().map(AnswerRow::id).toList());
        boolean managesCourse = actorOrNull != null
                && identity.canManageCourse(actorOrNull.userId(), question.courseId());

        List<AnswerView> answers = new ArrayList<>(answerRows.stream()
                .map(row -> toAnswerView(row, question, users, myVotes, commentRows, actorOrNull, managesCourse))
                .toList());
        answers.sort(answerOrder());

        return new QuestionDetailView(question.id(), question.title(), question.body(),
                renderer.toSafeHtml(question.body()), question.tags(), courseOf(question.courseId()),
                AuthorView.of(question.authorId(), users.get(question.authorId())), question.score(),
                question.answerCount(), question.viewCount(), question.acceptedAnswerId(),
                myVotes.getOrDefault("QUESTION:" + questionId, 0), question.createdAt(), question.updatedAt(),
                permissionsFor(question, actorOrNull, managesCourse),
                commentsOf(commentRows, "QUESTION", question.id(), users), answers);
    }

    public List<TagView> popularTags(int limit) {
        return readModel.popularTags(Math.min(Math.max(limit, 1), PageRequest.MAX_SIZE));
    }

    private AnswerView toAnswerView(AnswerRow row, QuestionRow question, Map<Long, UserSummary> users,
                                    Map<String, Integer> myVotes, List<CommentRow> commentRows,
                                    Actor actorOrNull, boolean managesCourse) {
        return new AnswerView(row.id(), row.body(), renderer.toSafeHtml(row.body()),
                AuthorView.of(row.authorId(), users.get(row.authorId())), row.score(),
                Objects.equals(question.acceptedAnswerId(), row.id()),
                row.endorsedBy() == null ? null : AuthorView.of(row.endorsedBy(), users.get(row.endorsedBy())),
                myVotes.getOrDefault("ANSWER:" + row.id(), 0),
                actorOrNull != null && actorOrNull.canManage(row.authorId(), managesCourse),
                row.createdAt(), row.updatedAt(), commentsOf(commentRows, "ANSWER", row.id(), users));
    }

    /** 被采纳的回答置顶，其后按得分降序、同分按时间升序。 */
    private static Comparator<AnswerView> answerOrder() {
        return Comparator.comparing((AnswerView a) -> !a.accepted())
                .thenComparing(AnswerView::score, Comparator.reverseOrder())
                .thenComparing(AnswerView::createdAt);
    }

    private List<CommentView> commentsOf(List<CommentRow> rows, String targetType, long targetId,
                                         Map<Long, UserSummary> users) {
        return rows.stream()
                .filter(row -> targetType.equals(row.targetType()) && row.targetId() == targetId)
                .map(row -> new CommentView(row.id(), AuthorView.of(row.authorId(), users.get(row.authorId())),
                        row.parentId(), row.body(), row.createdAt()))
                .toList();
    }

    /**
     * 前端据此显示按钮；后端在领域层仍会重新校验一遍，所以这里的结果只是提示，不是授权。
     * 判据与 Question 聚合保持一致：删除受「已有回答」限制，采纳受「已采纳」限制。
     */
    private PermissionsView permissionsFor(QuestionRow question, Actor actorOrNull, boolean managesCourse) {
        if (actorOrNull == null) {
            return PermissionsView.NONE;
        }
        boolean canManageContent = actorOrNull.canManage(question.authorId(), managesCourse);
        return new PermissionsView(canManageContent,
                canManageContent && (managesCourse || question.answerCount() == 0),
                canManageContent && question.acceptedAnswerId() == null,
                managesCourse);
    }

    private CourseRefView courseOf(Long courseId) {
        if (courseId == null) {
            return null;
        }
        return identity.findCourse(courseId)
                .map(course -> new CourseRefView(course.id(), course.code(), course.name()))
                .orElse(null);
    }
}
