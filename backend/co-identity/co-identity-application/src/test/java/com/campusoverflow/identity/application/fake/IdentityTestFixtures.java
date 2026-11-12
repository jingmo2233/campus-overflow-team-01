package com.campusoverflow.identity.application.fake;

import com.campusoverflow.identity.domain.Course;
import com.campusoverflow.identity.domain.CourseMember;
import com.campusoverflow.identity.domain.CourseRepository;
import com.campusoverflow.identity.domain.PasswordHasher;
import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserRepository;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.event.IntegrationEvent;
import com.campusoverflow.shared.event.IntegrationEventPublisher;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Identity 应用层的手写测试替身。
 *
 * <p>应用服务只依赖端口，因此这些用例不需要 Spring 容器与数据库；同时也不会掩盖
 * “应用层把校验交给了领域层”这一事实——替身只负责存取，不复制任何业务规则。</p>
 */
public final class IdentityTestFixtures {

    private IdentityTestFixtures() {
    }

    public static class InMemoryUsers implements UserRepository {
        private final Map<Long, User> store = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong();
        public int saveCount;

        @Override
        public Optional<User> findById(long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return store.values().stream().filter(u -> u.username().equals(username)).findFirst();
        }

        @Override
        public Optional<User> findByDisplayName(String displayName) {
            return store.values().stream().filter(u -> u.displayName().equals(displayName)).findFirst();
        }

        @Override
        public boolean existsByUsername(String username) {
            return store.values().stream().anyMatch(u -> u.username().equals(username));
        }

        @Override
        public boolean existsByDisplayName(String displayName) {
            return store.values().stream().anyMatch(u -> u.displayName().equals(displayName));
        }

        @Override
        public boolean existsByEmail(String email) {
            return store.values().stream().anyMatch(u -> u.email().equalsIgnoreCase(email));
        }

        @Override
        public List<User> findAllById(Collection<Long> ids) {
            return ids.stream().distinct().map(store::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public PageResult<User> findByVerified(boolean verified, PageRequest page) {
            List<User> matched = store.values().stream()
                    .filter(u -> u.verified() == verified)
                    .sorted(Comparator.comparing(User::id))
                    .toList();
            List<User> slice = matched.stream().skip(page.offset()).limit(page.size()).toList();
            return new PageResult<>(slice, matched.size(), page.page(), page.size());
        }

        @Override
        public User save(User user) {
            if (user.id() == null) {
                user.assignId(sequence.incrementAndGet());
            }
            store.put(user.id(), user);
            saveCount++;
            return user;
        }
    }

    public static class InMemoryCourses implements CourseRepository {
        private final Map<Long, Course> courses = new LinkedHashMap<>();
        private final Map<String, CourseMember> members = new LinkedHashMap<>();
        private final AtomicLong sequence = new AtomicLong();
        public int memberSaveCount;

        @Override
        public Course save(Course course) {
            if (course.id() == null) {
                course.assignId(sequence.incrementAndGet());
            }
            courses.put(course.id(), course);
            return course;
        }

        @Override
        public Optional<Course> findById(long id) {
            return Optional.ofNullable(courses.get(id));
        }

        @Override
        public List<Course> findAll() {
            return List.copyOf(courses.values());
        }

        @Override
        public boolean existsByCodeAndTerm(String code, String term) {
            return courses.values().stream().anyMatch(c -> c.code().equals(code) && c.term().equals(term));
        }

        @Override
        public Optional<CourseMember> findMember(long courseId, long userId) {
            return Optional.ofNullable(members.get(key(courseId, userId)));
        }

        @Override
        public CourseMember saveMember(CourseMember member) {
            members.put(key(member.courseId(), member.userId()), member);
            memberSaveCount++;
            return member;
        }

        @Override
        public List<CourseMember> findMembers(long courseId) {
            return members.values().stream().filter(m -> m.courseId() == courseId).toList();
        }

        @Override
        public List<CourseMember> findMembershipsOfUser(long userId) {
            return members.values().stream().filter(m -> m.userId() == userId).toList();
        }

        private static String key(long courseId, long userId) {
            return courseId + ":" + userId;
        }
    }

    /** 可预测的哈希，便于断言“存进去的不是明文”。 */
    public static class StubHasher implements PasswordHasher {
        public static final String PREFIX = "bcrypt$";

        @Override
        public String hash(String raw) {
            return PREFIX + Integer.toHexString(raw.hashCode());
        }

        @Override
        public boolean matches(String raw, String hash) {
            return hash(raw).equals(hash);
        }
    }

    public static class RecordingEvents implements IntegrationEventPublisher {
        public final List<IntegrationEvent> published = new ArrayList<>();

        @Override
        public void publish(IntegrationEvent event) {
            published.add(event);
        }

        public <T extends IntegrationEvent> List<T> ofType(Class<T> type) {
            return published.stream().filter(type::isInstance).map(type::cast).toList();
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
}
