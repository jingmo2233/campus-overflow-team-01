package com.campusoverflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.identity.application.fake.IdentityTestFixtures.InMemoryCourses;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.InMemoryUsers;
import com.campusoverflow.identity.domain.Course;
import com.campusoverflow.identity.domain.CourseMember;
import com.campusoverflow.identity.domain.CourseRole;
import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserStatus;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 「我的资料」查询：仅本人可见，带课程内角色。 */
class UserQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");

    private static final long MING_ID = 1L;
    private static final long TEACHER_ID = 9L;
    private static final long COURSE_ID = 100L;

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryCourses courses = new InMemoryCourses();
    private final UserQueryService service = new UserQueryService(users,
            new CourseService(courses, users, Clock.fixed(NOW, ZoneOffset.UTC)));

    @BeforeEach
    void setUp() {
        users.save(User.reconstitute(MING_ID, "2025001", "小明", "ming@campus.edu.cn", "hash", Role.STUDENT,
                UserStatus.ACTIVE, false, "计算机工程学院", "计科2301", NOW, 0));
        users.save(User.reconstitute(TEACHER_ID, "T9001", "王老师", "wang@campus.edu.cn", "hash", Role.TEACHER,
                UserStatus.ACTIVE, true, "计算机工程学院", null, NOW, 0));

        courses.save(Course.reconstitute(COURSE_ID, "CS2001", "数据结构", "2026-秋", TEACHER_ID, NOW));
        courses.saveMember(CourseMember.reconstitute(COURSE_ID, MING_ID, CourseRole.STUDENT, NOW));
    }

    @Test
    @DisplayName("返回本人完整资料：学号与邮箱只有本人能看到")
    void meReturnsFullProfile() {
        MeView me = service.me(MING_ID);

        assertThat(me.username()).isEqualTo("2025001");
        assertThat(me.email()).isEqualTo("ming@campus.edu.cn");
        assertThat(me.role()).isEqualTo(Role.STUDENT);
        assertThat(me.verified()).isFalse();
        assertThat(me.courses()).extracting(CourseView::code).contains("CS2001");
        assertThat(me.courses().get(0).myRole()).isEqualTo("STUDENT");
    }

    @Test
    @DisplayName("没有加入任何课程时课程列表为空而不是 null")
    void meReturnsEmptyCourseListWhenNotEnrolled() {
        MeView me = service.me(TEACHER_ID);

        assertThat(me.courses()).isNotNull();
        assertThat(me.courses()).isEmpty();
    }

    @Test
    @DisplayName("用户不存在时返回 404 语义")
    void meRejectsUnknownUser() {
        assertThatThrownBy(() -> service.me(404L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("用户不存在");
    }
}
