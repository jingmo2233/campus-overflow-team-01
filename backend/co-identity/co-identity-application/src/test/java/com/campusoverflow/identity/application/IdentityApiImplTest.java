package com.campusoverflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.identity.application.fake.IdentityTestFixtures.InMemoryCourses;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.InMemoryUsers;
import com.campusoverflow.identity.domain.Course;
import com.campusoverflow.identity.domain.CourseMember;
import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserStatus;
import com.campusoverflow.shared.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 开放主机服务：跨上下文只暴露摘要，且不泄漏学号/邮箱等个人信息。 */
class IdentityApiImplTest {

    private static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");

    private static final long ADMIN_ID = 99L;
    private static final long TEACHER_ID = 9L;
    private static final long STUDENT_ID = 1L;
    private static final long TA_ID = 2L;
    private static final long COURSE_ID = 100L;

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryCourses courses = new InMemoryCourses();
    private final IdentityApiImpl api = new IdentityApiImpl(users, courses);

    @BeforeEach
    void setUp() {
        users.save(user(ADMIN_ID, "admin", "系统管理员", Role.ADMIN));
        users.save(user(TEACHER_ID, "T9001", "王老师", Role.TEACHER));
        users.save(user(STUDENT_ID, "2025001", "小明", Role.STUDENT));
        users.save(user(TA_ID, "2025002", "小红", Role.TA));

        courses.save(Course.reconstitute(COURSE_ID, "CS2001", "数据结构", "2026-秋", TEACHER_ID, NOW));
        courses.saveMember(CourseMember.reconstitute(COURSE_ID, TA_ID, com.campusoverflow.identity.domain.CourseRole.TA,
                NOW));
        courses.saveMember(CourseMember.reconstitute(COURSE_ID, STUDENT_ID,
                com.campusoverflow.identity.domain.CourseRole.STUDENT, NOW));
    }

    @Test
    @DisplayName("按 id 查询返回摘要，不包含学号")
    void findUserReturnsSummary() {
        var summary = api.findUser(STUDENT_ID);

        assertThat(summary).isPresent();
        assertThat(summary.get().displayName()).isEqualTo("小明");
        assertThat(summary.get().role()).isEqualTo(Role.STUDENT);
        assertThat(summary.get().verified()).isFalse();
        assertThat(summary.get().college()).isEqualTo("计算机工程学院");
    }

    @Test
    @DisplayName("批量查询去重，空集合直接返回空 Map")
    void findUsersDeduplicatesAndHandlesEmpty() {
        Map<Long, ?> batch = api.findUsers(List.of(STUDENT_ID, STUDENT_ID, TA_ID));
        assertThat(batch).hasSize(2).containsKeys(STUDENT_ID, TA_ID);
        assertThat(api.findUsers(List.of())).isEmpty();
        assertThat(api.findUsers(null)).isEmpty();
    }

    @Test
    @DisplayName("按昵称反查（@提及解析用）")
    void findByDisplayName() {
        assertThat(api.findByDisplayName("小明")).get()
                .extracting(s -> s.id()).isEqualTo(STUDENT_ID);
        assertThat(api.findByDisplayName("查无此人")).isEmpty();
    }

    @Test
    @DisplayName("课程摘要包含教师 id 但不跨上下文取教师详情")
    void findCourseReturnsSummary() {
        var course = api.findCourse(COURSE_ID);

        assertThat(course).isPresent();
        assertThat(course.get().code()).isEqualTo("CS2001");
        assertThat(course.get().teacherId()).isEqualTo(TEACHER_ID);
        assertThat(api.findCourse(404L)).isEmpty();
    }

    @Test
    @DisplayName("管理员在任何课程都能管理")
    void adminManagesEveryCourse() {
        assertThat(api.canManageCourse(ADMIN_ID, COURSE_ID)).isTrue();
        assertThat(api.canManageCourse(ADMIN_ID, null)).isTrue();
    }

    @Test
    @DisplayName("教师在没有课程上下文时视为可管理")
    void teacherManagesWithoutCourseContext() {
        assertThat(api.canManageCourse(TEACHER_ID, null)).isTrue();
    }

    @Test
    @DisplayName("课程内角色决定权限：助教可管理，学生不可")
    void courseRoleDecidesManagement() {
        assertThat(api.canManageCourse(TA_ID, COURSE_ID)).isTrue();
        assertThat(api.canManageCourse(STUDENT_ID, COURSE_ID)).isFalse();
    }

    @Test
    @DisplayName("全局教师但不是该课程成员时不可管理该课程")
    void teacherOutsideCourseCannotManage() {
        assertThat(api.canManageCourse(TEACHER_ID, 404L)).isFalse();
    }

    @Test
    @DisplayName("用户不存在时一律不可管理")
    void unknownUserCannotManage() {
        assertThat(api.canManageCourse(404L, COURSE_ID)).isFalse();
        assertThat(api.canManageCourse(404L, null)).isFalse();
    }

    private static User user(long id, String username, String displayName, Role role) {
        return User.reconstitute(id, username, displayName, username + "@campus.edu.cn", "hash", role,
                UserStatus.ACTIVE, role != Role.STUDENT, "计算机工程学院", "计科2301", NOW, 0);
    }
}
