package com.campusoverflow.reputation.application.fake;

import com.campusoverflow.identity.api.CourseSummary;
import com.campusoverflow.identity.api.IdentityApi;
import com.campusoverflow.identity.api.UserSummary;
import com.campusoverflow.qa.api.QaQueryApi;
import com.campusoverflow.qa.api.QuestionRef;
import com.campusoverflow.reputation.application.LeaderboardPeriod;
import com.campusoverflow.reputation.application.LeaderboardStore;
import com.campusoverflow.reputation.domain.BadgeAward;
import com.campusoverflow.reputation.domain.BadgeRepository;
import com.campusoverflow.reputation.domain.BadgeType;
import com.campusoverflow.reputation.domain.Bounty;
import com.campusoverflow.reputation.domain.BountyRepository;
import com.campusoverflow.reputation.domain.BountyStatus;
import com.campusoverflow.reputation.domain.LedgerEntry;
import com.campusoverflow.reputation.domain.LedgerRepository;
import com.campusoverflow.reputation.domain.ReputationAccount;
import com.campusoverflow.reputation.domain.ReputationAccountRepository;
import com.campusoverflow.reputation.domain.ReputationReason;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.event.IntegrationEvent;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.event.ProcessedEventStore;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 手写测试替身。应用服务只依赖端口接口，因此不需要 Spring 容器也不需要数据库，
 * 每个用例都在毫秒级完成。刻意不引入 Mockito：这里的替身要能表达「余额被我改过没有」
 * 这类状态断言，手写反而更直白。
 */
public final class ReputationTestFixtures {

    private ReputationTestFixtures() {
    }

    /** 固定时钟，让「每日上限」「到期判定」这类时间相关用例可重复。 */
    public static Clock fixedClock(String isoInstant) {
        Instant instant = Instant.parse(isoInstant);
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    public static class InMemoryAccounts implements ReputationAccountRepository {
        private final Map<Long, ReputationAccount> store = new LinkedHashMap<>();

        public InMemoryAccounts with(ReputationAccount account) {
            store.put(account.userId(), account);
            return this;
        }

        public ReputationAccount get(long userId) {
            return store.get(userId);
        }

        @Override
        public Optional<ReputationAccount> findByUserId(long userId) {
            return Optional.ofNullable(store.get(userId));
        }

        @Override
        public List<ReputationAccount> findAllByUserId(Collection<Long> userIds) {
            return userIds.stream().map(store::get).filter(a -> a != null).toList();
        }

        @Override
        public List<ReputationAccount> topByReputation(int limit) {
            return store.values().stream()
                    .sorted(Comparator.comparingInt(ReputationAccount::reputation).reversed()
                            .thenComparingLong(ReputationAccount::userId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<ReputationAccount> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public ReputationAccount save(ReputationAccount account) {
            store.put(account.userId(), account);
            return account;
        }
    }

    /**
     * 内存流水。exists 用 (eventId, userId, reason) 做唯一键，和真实表的唯一索引语义一致，
     * 于是「重复投递不加分」这条用例测的就是真实约束。
     */
    public static class InMemoryLedger implements LedgerRepository {
        private final List<LedgerEntry> entries = new ArrayList<>();
        private final Set<String> keys = new LinkedHashSet<>();
        private final AtomicLong sequence = new AtomicLong();
        private Instant now = Instant.parse("2026-10-27T08:00:00Z");

        public void useNow(Instant instant) {
            this.now = instant;
        }

        public List<LedgerEntry> entries() {
            return List.copyOf(entries);
        }

        @Override
        public boolean exists(String eventId, long userId, ReputationReason reason) {
            return keys.contains(eventId + "|" + userId + "|" + reason);
        }

        @Override
        public void append(LedgerEntry entry) {
            LedgerEntry saved = new LedgerEntry(sequence.incrementAndGet(), entry.userId(), entry.eventId(),
                    entry.reason(), entry.delta(), entry.refType(), entry.refId(),
                    entry.createdAt() == null ? now : entry.createdAt());
            entries.add(saved);
            keys.add(saved.eventId() + "|" + saved.userId() + "|" + saved.reason());
        }

        @Override
        public PageResult<LedgerEntry> findByUser(long userId, PageRequest page) {
            List<LedgerEntry> mine = entries.stream()
                    .filter(e -> e.userId() == userId)
                    .sorted(Comparator.comparing(LedgerEntry::id).reversed())
                    .toList();
            int from = Math.min(page.offset(), mine.size());
            int to = Math.min(from + page.size(), mine.size());
            return new PageResult<>(mine.subList(from, to), mine.size(), page.page(), page.size());
        }

        @Override
        public long countByUserAndReason(long userId, ReputationReason reason) {
            return entries.stream().filter(e -> e.userId() == userId && e.reason() == reason).count();
        }

        @Override
        public int sumPositiveVoteDeltaSince(long userId, Instant since) {
            return entries.stream()
                    .filter(e -> e.userId() == userId && e.reason().isVote() && e.delta() > 0)
                    .filter(e -> !e.createdAt().isBefore(since))
                    .mapToInt(LedgerEntry::delta)
                    .sum();
        }

        @Override
        public Map<Long, Long> sumByUser() {
            Map<Long, Long> sums = new HashMap<>();
            entries.forEach(e -> sums.merge(e.userId(), (long) e.delta(), Long::sum));
            return sums;
        }
    }

    public static class InMemoryBadges implements BadgeRepository {
        private final Set<String> keys = new LinkedHashSet<>();
        private final List<BadgeAward> awards = new ArrayList<>();

        @Override
        public boolean has(long userId, BadgeType type) {
            return keys.contains(userId + "|" + type);
        }

        @Override
        public void award(BadgeAward award) {
            awards.add(award);
            keys.add(award.userId() + "|" + award.type());
        }

        @Override
        public List<BadgeAward> findByUser(long userId) {
            return awards.stream().filter(a -> a.userId() == userId).toList();
        }
    }

    public static class InMemoryBounties implements BountyRepository {
        private final Map<Long, Bounty> store = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        public InMemoryBounties with(Bounty bounty) {
            if (bounty.id() == null) {
                bounty.assignId(sequence.incrementAndGet());
            }
            store.put(bounty.id(), bounty);
            return this;
        }

        @Override
        public Optional<Bounty> findOpenByQuestion(long questionId) {
            return store.values().stream()
                    .filter(b -> b.questionId() == questionId && b.status() == BountyStatus.OPEN)
                    .findFirst();
        }

        @Override
        public Optional<Bounty> findLatestByQuestion(long questionId) {
            return store.values().stream()
                    .filter(b -> b.questionId() == questionId)
                    .max(Comparator.comparing(Bounty::id));
        }

        @Override
        public List<Bounty> findDue(Instant now, int limit) {
            return store.values().stream()
                    .filter(b -> b.isDue(now))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Bounty save(Bounty bounty) {
            if (bounty.id() == null) {
                bounty.assignId(sequence.incrementAndGet());
            }
            store.put(bounty.id(), bounty);
            return bounty;
        }
    }

    public static class RecordingEvents implements IntegrationEventPublisher {
        public final List<IntegrationEvent> published = new ArrayList<>();

        @Override
        public void publish(IntegrationEvent event) {
            published.add(event);
        }

        public <T extends IntegrationEvent> List<T> ofType(Class<T> type) {
            return published.stream().filter(type::isInstance).map(type::cast).collect(Collectors.toList());
        }

        public boolean isEmpty() {
            return published.isEmpty();
        }
    }

    public static class RecordingAudit implements AuditTrail {
        public final List<String> actions = new ArrayList<>();
        public final List<String> details = new ArrayList<>();

        @Override
        public void record(String action, Long actorId, String targetType, String targetId, String detail) {
            actions.add(action);
            details.add(detail);
        }
    }

    public static class StubIdentity implements IdentityApi {
        private final Map<Long, UserSummary> users = new HashMap<>();
        private final Set<Long> courseManagers = new LinkedHashSet<>();

        public StubIdentity withUser(long id, String name, Role role) {
            users.put(id, new UserSummary(id, name, role, true, "计算机工程学院"));
            return this;
        }

        /** 未通过学校身份认证的账号：榜单上要显示“未认证”标记。 */
        public StubIdentity withUnverifiedUser(long id, String name, Role role) {
            users.put(id, new UserSummary(id, name, role, false, "计算机工程学院"));
            return this;
        }

        public StubIdentity withCourseManager(long userId) {
            courseManagers.add(userId);
            return this;
        }

        @Override
        public Optional<UserSummary> findUser(long userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public Map<Long, UserSummary> findUsers(Collection<Long> userIds) {
            Map<Long, UserSummary> result = new HashMap<>();
            userIds.forEach(id -> findUser(id).ifPresent(u -> result.put(id, u)));
            return result;
        }

        @Override
        public Optional<UserSummary> findByDisplayName(String displayName) {
            return users.values().stream().filter(u -> u.displayName().equals(displayName)).findFirst();
        }

        @Override
        public Optional<CourseSummary> findCourse(long courseId) {
            return Optional.of(new CourseSummary(courseId, "SE2026", "软件工程", "2026-秋", 900L));
        }

        @Override
        public boolean canManageCourse(long userId, Long courseId) {
            return courseManagers.contains(userId);
        }
    }

    public static class StubQa implements QaQueryApi {
        private final Map<Long, QuestionRef> questions = new HashMap<>();

        public StubQa withQuestion(long id, long authorId, Long courseId, Long acceptedAnswerId, boolean deleted) {
            questions.put(id, new QuestionRef(id, authorId, courseId, "如何理解领域事件", acceptedAnswerId, deleted));
            return this;
        }

        @Override
        public Optional<QuestionRef> findQuestion(long questionId) {
            return Optional.ofNullable(questions.get(questionId));
        }
    }

    /** 只记录 increment 的调用与参数，周期分桶逻辑本身由基础设施层的 Redis 实现负责。 */
    public static class InMemoryLeaderboard implements LeaderboardStore {
        public final List<Entry> increments = new ArrayList<>();
        public final Map<LeaderboardPeriod, List<Entry>> boards = new HashMap<>();

        public InMemoryLeaderboard withBoard(LeaderboardPeriod period, Entry... entries) {
            boards.put(period, new ArrayList<>(List.of(entries)));
            return this;
        }

        @Override
        public void increment(long userId, int delta, Instant at) {
            increments.add(new Entry(userId, delta));
        }

        @Override
        public List<Entry> top(LeaderboardPeriod period, Instant at, int limit) {
            return boards.getOrDefault(period, List.of()).stream().limit(limit).toList();
        }
    }

    /** 与真实实现一致：同一 eventId + consumer 第二次调用返回 false。 */
    public static class InMemoryProcessed implements ProcessedEventStore {
        private final Set<String> keys = new LinkedHashSet<>();

        @Override
        public boolean markProcessed(String eventId, String consumer) {
            return keys.add(eventId + "|" + consumer);
        }
    }
}
