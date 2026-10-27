package com.campusoverflow.discovery.application.fake;

import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationRepository;
import com.campusoverflow.discovery.domain.notification.NotificationType;
import com.campusoverflow.discovery.domain.search.SearchDocument;
import com.campusoverflow.discovery.domain.search.SearchHit;
import com.campusoverflow.discovery.domain.search.SearchIndex;
import com.campusoverflow.discovery.domain.search.SearchPort;
import com.campusoverflow.discovery.domain.search.SearchQuery;
import com.campusoverflow.identity.api.CourseSummary;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.shared.event.ProcessedEventStore;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Discovery 应用层的全部测试替身（手写，不引 Mockito，也不启动 Spring）。 */
public final class DiscoveryTestFixtures {

    private DiscoveryTestFixtures() {
    }

    public static UserSummary user(long id, String displayName, Role role) {
        return new UserSummary(id, displayName, role, false, "计算机学院");
    }

    public static Instant at(String iso) {
        return Instant.parse(iso);
    }

    /** 记录投影写入，便于断言「调了哪个方法、参数对不对」。 */
    public static final class RecordingSearchIndex implements SearchIndex {

        public final List<SearchDocument> inserts = new ArrayList<>();
        public final List<String> textUpdates = new ArrayList<>();
        public final List<String> answerDeltas = new ArrayList<>();
        public final List<String> scoreDeltas = new ArrayList<>();
        public final List<Long> deleted = new ArrayList<>();
        public final List<Long> accepted = new ArrayList<>();
        public final List<String> bountyFlags = new ArrayList<>();
        public List<String> tags = List.of();
        public List<RelatedCandidate> candidates = List.of();
        public int lastCandidateLimit;

        @Override
        public void insert(SearchDocument document) {
            inserts.add(document);
        }

        @Override
        public void updateText(long questionId, String title, String bodyText, List<String> tags, Instant at) {
            textUpdates.add(questionId + "|" + title + "|" + bodyText + "|" + String.join(",", tags));
        }

        @Override
        public void adjustAnswerCount(long questionId, int delta, Instant at) {
            answerDeltas.add(questionId + ":" + delta);
        }

        @Override
        public void adjustScore(long questionId, int delta) {
            scoreDeltas.add(questionId + ":" + delta);
        }

        @Override
        public void markAccepted(long questionId, Instant at) {
            accepted.add(questionId);
        }

        @Override
        public void setBountyOpen(long questionId, boolean open) {
            bountyFlags.add(questionId + ":" + open);
        }

        @Override
        public void markDeleted(long questionId) {
            deleted.add(questionId);
        }

        @Override
        public List<String> findTags(long questionId) {
            return tags;
        }

        @Override
        public List<RelatedCandidate> findCandidatesSharingTags(long questionId, List<String> tags, int limit) {
            lastCandidateLimit = limit;
            return candidates;
        }
    }

    /** 只认 eventId，模拟 (consumer, eventId) 唯一约束。 */
    public static final class InMemoryProcessed implements ProcessedEventStore {

        private final Set<String> seen = new LinkedHashSet<>();

        @Override
        public boolean markProcessed(String eventId, String consumer) {
            return seen.add(consumer + "|" + eventId);
        }

        public int size() {
            return seen.size();
        }
    }

    public static final class InMemoryNotifications implements NotificationRepository {

        public final List<Notification> saved = new ArrayList<>();
        private final Map<Long, Notification> byId = new LinkedHashMap<>();
        public final Set<String> keys = new LinkedHashSet<>();
        public long unread;
        public long nextId = 100;

        @Override
        public boolean exists(String sourceEventId, long recipientId) {
            return keys.contains(recipientId + "|" + sourceEventId);
        }

        @Override
        public Notification save(Notification notification) {
            if (notification.id() == null) {
                notification.assignId(nextId++);
            }
            byId.put(notification.id(), notification);
            keys.add(notification.recipientId() + "|" + notification.sourceEventId());
            saved.add(notification);
            return notification;
        }

        @Override
        public Optional<Notification> findById(long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public PageResult<Notification> findByRecipient(long recipientId, PageRequest page) {
            List<Notification> mine = saved.stream().filter(n -> n.recipientId() == recipientId).toList();
            int from = Math.min(page.offset(), mine.size());
            int to = Math.min(from + page.size(), mine.size());
            return new PageResult<>(mine.subList(from, to), mine.size(), page.page(), page.size());
        }

        @Override
        public long countUnread(long recipientId) {
            return unread;
        }

        @Override
        public int markAllRead(long recipientId) {
            int updated = (int) saved.stream()
                    .filter(n -> n.recipientId() == recipientId && !n.read()).count();
            saved.stream().filter(n -> n.recipientId() == recipientId).forEach(n -> n.markRead(recipientId));
            unread = 0;
            return updated;
        }

        public Notification only() {
            if (saved.size() != 1) {
                throw new AssertionError("期望 1 条通知，实际 " + saved.size() + " 条");
            }
            return saved.get(0);
        }

        public NotificationType typeOfOnly() {
            return only().type();
        }
    }

    public static final class StubIdentity implements IdentityApi {

        public final Map<Long, UserSummary> users = new LinkedHashMap<>();
        public final Map<String, Long> byName = new LinkedHashMap<>();
        public int findUsersCalls;

        public StubIdentity with(UserSummary summary) {
            users.put(summary.id(), summary);
            byName.put(summary.displayName(), summary.id());
            return this;
        }

        @Override
        public Optional<UserSummary> findUser(long userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public Map<Long, UserSummary> findUsers(Collection<Long> userIds) {
            findUsersCalls++;
            Map<Long, UserSummary> found = new LinkedHashMap<>();
            userIds.forEach(id -> {
                if (users.containsKey(id)) {
                    found.put(id, users.get(id));
                }
            });
            return found;
        }

        @Override
        public Optional<UserSummary> findByDisplayName(String displayName) {
            Long id = byName.get(displayName);
            return id == null ? Optional.empty() : Optional.ofNullable(users.get(id));
        }

        @Override
        public Optional<CourseSummary> findCourse(long courseId) {
            return Optional.empty();
        }

        @Override
        public boolean canManageCourse(long userId, Long courseId) {
            return false;
        }
    }

    public static final class StubQa implements QaQueryApi {

        public final Map<Long, QuestionRef> questions = new LinkedHashMap<>();

        public StubQa with(QuestionRef ref) {
            questions.put(ref.id(), ref);
            return this;
        }

        @Override
        public Optional<QuestionRef> findQuestion(long questionId) {
            return Optional.ofNullable(questions.get(questionId));
        }
    }

    public static final class StubSearchPort implements SearchPort {

        public SearchQuery lastQuery;
        public PageResult<SearchHit> result = PageResult.empty(1, 20);

        @Override
        public PageResult<SearchHit> search(SearchQuery query) {
            lastQuery = query;
            return result;
        }
    }
}
