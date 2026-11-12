package com.campusoverflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.identity.api.event.UserRegisteredEvent;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.InMemoryUsers;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.RecordingAudit;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.RecordingEvents;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.StubHasher;
import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserStatus;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.page.PageResult;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 管理端用户用例：认证、教职工创建、角色调整与越权拦截。 */
class AdminUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");

    private static final long ADMIN_ID = 99L;
    private static final long STUDENT_ID = 1L;
    private static final long TEACHER_ID = 9L;

    private final InMemoryUsers users = new InMemoryUsers();
    private final StubHasher hasher = new StubHasher();
    private final RecordingEvents events = new RecordingEvents();
    private final RecordingAudit audit = new RecordingAudit();
    private final AdminUserService service = new AdminUserService(users, hasher, events, audit,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final Actor admin = new Actor(ADMIN_ID, "系统管理员", Role.ADMIN);
    private final Actor student = new Actor(STUDENT_ID, "小明", Role.STUDENT);
    private final Actor teacher = new Actor(TEACHER_ID, "王老师", Role.TEACHER);

    @BeforeEach
    void setUp() {
        users.save(user(ADMIN_ID, "admin", "系统管理员", Role.ADMIN, true));
        users.save(user(STUDENT_ID, "2025001", "小明", Role.STUDENT, false));
        users.save(user(TEACHER_ID, "T9001", "王老师", Role.TEACHER, true));
    }

    @Test
    @DisplayName("管理员认证学生后，verified 变为 true 并写审计")
    void verifyMarksUserVerified() {
        service.verify(admin, STUDENT_ID);

        assertThat(users.findById(STUDENT_ID).orElseThrow().verified()).isTrue();
        assertThat(audit.actions).contains("USER_VERIFIED");
    }

    @Test
    @DisplayName("重复认证同一个用户被拒绝")
    void verifyTwiceRejected() {
        service.verify(admin, STUDENT_ID);

        assertThatThrownBy(() -> service.verify(admin, STUDENT_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已完成认证");
    }

    @Test
    @DisplayName("非管理员不能认证用户")
    void nonAdminCannotVerify() {
        assertThatThrownBy(() -> service.verify(student, TEACHER_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅管理员");
    }

    @Test
    @DisplayName("认证不存在的用户返回 404")
    void verifyUnknownUserRejected() {
        assertThatThrownBy(() -> service.verify(admin, 404L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("管理员创建教师账号：创建即认证，并发布注册事件")
    void createStaffIsVerifiedByDefault() {
        long id = service.createStaff(admin, new CreateStaffCommand("T9002", "李老师", "li@campus.edu.cn",
                "Campus2026", Role.TEACHER, "计算机工程学院"));

        User saved = users.findById(id).orElseThrow();
        assertThat(saved.role()).isEqualTo(Role.TEACHER);
        assertThat(saved.verified()).isTrue();
        assertThat(saved.className()).isNull();
        assertThat(saved.passwordHash()).isEqualTo(hasher.hash("Campus2026"));

        assertThat(events.ofType(UserRegisteredEvent.class))
                .extracting(UserRegisteredEvent::role)
                .contains(Role.TEACHER);
        assertThat(audit.actions).contains("STAFF_CREATED");
    }

    @Test
    @DisplayName("不能用这个入口创建学生账号")
    void createStaffRejectsStudentRole() {
        assertThatThrownBy(() -> service.createStaff(admin, new CreateStaffCommand("2025999", "假学生",
                "fake@campus.edu.cn", "Campus2026", Role.STUDENT, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("注册流程");
    }

    @Test
    @DisplayName("工号重复时创建教职工失败")
    void createStaffRejectsDuplicateUsername() {
        assertThatThrownBy(() -> service.createStaff(admin, new CreateStaffCommand("T9001", "另一个老师",
                "another@campus.edu.cn", "Campus2026", Role.TEACHER, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已被使用");
    }

    @Test
    @DisplayName("调整角色成功，审计里记录变更前后")
    void changeRoleAuditsTransition() {
        service.changeRole(admin, STUDENT_ID, Role.TA);

        assertThat(users.findById(STUDENT_ID).orElseThrow().role()).isEqualTo(Role.TA);
        assertThat(audit.details).anyMatch(detail -> "STUDENT -> TA".equals(detail));
    }

    @Test
    @DisplayName("不能修改自己的角色")
    void cannotChangeOwnRole() {
        assertThatThrownBy(() -> service.changeRole(admin, ADMIN_ID, Role.STUDENT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不能修改自己");
    }

    @Test
    @DisplayName("非管理员不能调整角色")
    void nonAdminCannotChangeRole() {
        assertThatThrownBy(() -> service.changeRole(teacher, STUDENT_ID, Role.TA))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("仅管理员");
    }

    @Test
    @DisplayName("待认证列表只返回未认证用户，且学号已脱敏")
    void listUnverifiedMasksUsername() {
        PageResult<UserView> page = service.listByVerification(admin, false, new PageRequest(1, 10));

        assertThat(page.items()).extracting(UserView::id).containsExactly(STUDENT_ID);
        assertThat(page.items().get(0).maskedUsername()).isEqualTo("2025****01");
        assertThat(page.items().get(0).maskedUsername()).doesNotContain("2025001");
    }

    @Test
    @DisplayName("非管理员不能查看待认证列表")
    void nonAdminCannotListByVerification() {
        assertThatThrownBy(() -> service.listByVerification(student, false, new PageRequest(1, 10)))
                .isInstanceOf(ForbiddenException.class);
    }

    private static User user(long id, String username, String displayName, Role role, boolean verified) {
        return User.reconstitute(id, username, displayName, username + "@campus.edu.cn", "hash", role,
                UserStatus.ACTIVE, verified, "计算机工程学院", "计科2301", NOW, 0);
    }
}
