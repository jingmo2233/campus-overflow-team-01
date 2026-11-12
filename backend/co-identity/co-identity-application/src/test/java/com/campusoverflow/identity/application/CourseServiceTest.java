package com.campusoverflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.identity.application.fake.IdentityTestFixtures.InMemoryCourses;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.InMemoryUsers;
import com.campusoverflow.identity.domain.CourseMember;
import com.campusoverflow.identity.domain.CourseRole;
import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserStatus;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 课程用例：建课权限、唯一性、加入流程与成员角色调整。 */
class CourseServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");

    private static final long MING_ID = 1L;
    private static final long HONG_ID = 2L;
    private static final long TEACHER_ID = 9L;

    private final InMemoryCourses courses = new InMemoryCourses();
    private final InMemoryUsers users = new InMemoryUsers();
    private final CourseService service = new CourseService(courses, users, Clock.fixed(NOW, ZoneOffset.UTC));

    private final Actor teacher = new Actor(TEACHER_ID, "王老师", Role.TEACHER);
    private final Actor student = new Actor(MING_ID, "小明", Role.STUDENT);
    private final Actor admin = new Actor(99L, "系统管理员", Role.ADMIN);

    private long courseId;

    @BeforeEach
    void setUp() {
        users.save(newUser(MING_ID, "2025001", "小明", Role.STUDENT));
        users.save(newUser(HONG_ID, "2025002", "小红", Role.STUDENT));
        users.save(newUser(TEACHER_ID, "T9001", "王老师", Role.TEACHER));
        courseId = service.create(teacher, new CreateCourseCommand("CS2001", "数据结构", "2026-秋"));
    }

    @Test
    @DisplayName("建课成功后创建者自动成为课程教师成员")
    void creatorBecomesTeacherMember() {
        assertThat(courseId).isPositive();
        assertThat(courses.findMember(courseId, TEACHER_ID))
                .isPresent()
                .get()
                .extracting(CourseMember::role)
                .isEqualTo(CourseRole.TEACHER);
    }

    @Test
    @DisplayName("学生不能建课")
    void studentCannotCreateCourse() {
        assertThatThrownBy(() -> service.create(student, new CreateCourseCommand("CS2002", "操作系统", "2026-秋")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("教师或管理员");
    }

    @Test
    @DisplayName("同一学期内课程代码唯一，换学期则允许")
    void duplicateCodeInSameTermRejected() {
        assertThatThrownBy(() -> service.create(teacher, new CreateCourseCommand("CS2001", "数据结构（二）", "2026-秋")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("同代码课程");

        long nextTerm = service.create(teacher, new CreateCourseCommand("CS2001", "数据结构", "2027-春"));
        assertThat(nextTerm).isNotEqualTo(courseId);
    }

    @Test
    @DisplayName("加入课程：首次成功，重复加入被拒绝")
    void joinTwiceRejected() {
        service.join(student, courseId);
        assertThat(courses.findMember(courseId, MING_ID))
                .isPresent()
                .get()
                .extracting(CourseMember::role)
                .isEqualTo(CourseRole.STUDENT);

        assertThatThrownBy(() -> service.join(student, courseId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已加入");
    }

    @Test
    @DisplayName("加入不存在的课程返回 404 语义")
    void joinUnknownCourseRejected() {
        assertThatThrownBy(() -> service.join(student, 999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("课程不存在");
    }

    @Test
    @DisplayName("课程教师可以把学生任命为助教")
    void teacherCanAssignTa() {
        service.join(student, courseId);

        service.assignRole(teacher, courseId, MING_ID, CourseRole.TA);

        Optional<CourseMember> member = courses.findMember(courseId, MING_ID);
        assertThat(member).isPresent();
        assertThat(member.get().role()).isEqualTo(CourseRole.TA);
    }

    @Test
    @DisplayName("非课程教师不能调整成员角色，管理员可以")
    void onlyTeacherOrAdminCanAssignRole() {
        service.join(student, courseId);

        assertThatThrownBy(() -> service.assignRole(student, courseId, MING_ID, CourseRole.TA))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("课程教师或管理员");

        service.assignRole(admin, courseId, MING_ID, CourseRole.TA);
        Optional<CourseMember> member = courses.findMember(courseId, MING_ID);
        assertThat(member).isPresent();
        assertThat(member.get().role()).isEqualTo(CourseRole.TA);
    }

    @Test
    @DisplayName("给非成员分配角色返回 404")
    void assignRoleToNonMemberRejected() {
        assertThatThrownBy(() -> service.assignRole(teacher, courseId, HONG_ID, CourseRole.TA))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("不是课程成员");
    }

    @Test
    @DisplayName("课程教师的角色不能被降级")
    void teacherRoleCannotBeDowngraded() {
        assertThatThrownBy(() -> service.assignRole(teacher, courseId, TEACHER_ID, CourseRole.STUDENT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("课程教师");
    }

    @Test
    @DisplayName("课程列表：未登录时 myRole 为空，已加入时带课程内角色")
    void listAllCarriesMyRole() {
        service.join(student, courseId);

        List<CourseView> anonymous = service.listAll(null);
        assertThat(anonymous).hasSize(1);
        assertThat(anonymous.get(0).myRole()).isNull();
        assertThat(anonymous.get(0).teacherName()).isEqualTo("王老师");

        List<CourseView> asStudent = service.listAll(student);
        assertThat(asStudent.get(0).myRole()).isEqualTo("STUDENT");
    }

    @Test
    @DisplayName("我的课程只返回已加入的课程")
    void myCoursesFiltersByMembership() {
        long other = service.create(teacher, new CreateCourseCommand("CS2002", "操作系统", "2026-秋"));
        service.join(student, courseId);

        List<CourseView> mine = service.myCourses(MING_ID);

        assertThat(mine).extracting(CourseView::id).containsExactly(courseId);
        assertThat(mine).extracting(CourseView::id).doesNotContain(other);
    }

    @Test
    @DisplayName("成员列表带昵称，按加入情况返回")
    void membersResolveDisplayNames() {
        service.join(student, courseId);

        List<CourseMemberView> members = service.members(courseId);

        assertThat(members).extracting(CourseMemberView::displayName).contains("小明", "王老师");
    }

    private static User newUser(long id, String username, String displayName, Role role) {
        return User.reconstitute(id, username, displayName, username + "@campus.edu.cn", "hash", role,
                UserStatus.ACTIVE, role != Role.STUDENT, "计算机工程学院", "计科2301", NOW, 0);
    }
}
