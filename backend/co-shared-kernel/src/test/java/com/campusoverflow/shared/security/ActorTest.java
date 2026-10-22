package com.campusoverflow.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActorTest {

    private static final long AUTHOR_ID = 7L;

    private final Actor author = new Actor(AUTHOR_ID, "作者", Role.STUDENT);
    private final Actor stranger = new Actor(99, "路人", Role.STUDENT);
    private final Actor teacher = new Actor(9, "王老师", Role.TEACHER);
    private final Actor ta = new Actor(8, "李助教", Role.TA);
    private final Actor admin = new Actor(1, "管理员", Role.ADMIN);

    @Test
    void authorCanManageOwnContentWithoutCoursePrivilege() {
        assertThat(author.canManage(AUTHOR_ID, false)).isTrue();
    }

    @Test
    void courseManagerCanManageOthersContent() {
        assertThat(teacher.canManage(AUTHOR_ID, true)).isTrue();
        assertThat(ta.canManage(AUTHOR_ID, true)).isTrue();
        assertThat(admin.canManage(AUTHOR_ID, true)).isTrue();
    }

    /**
     * 关键约束：全局角色是 TEACHER 并不意味着管得了每一门课。
     * managesCourse 由应用层查 Identity 上下文的课程成员关系后传入，
     * 领域层不得改用 isTeacherOrAdmin() 代替。
     */
    @Test
    void teacherWhoDoesNotManageTheCourseIsTreatedAsStranger() {
        assertThat(teacher.canManage(AUTHOR_ID, false)).isFalse();
        assertThat(ta.canManage(AUTHOR_ID, false)).isFalse();
        assertThat(admin.canManage(AUTHOR_ID, false)).isFalse();
    }

    @Test
    void strangerCanManageNothing() {
        assertThat(stranger.canManage(AUTHOR_ID, false)).isFalse();
    }

    @Test
    void identityHelpersReflectRole() {
        assertThat(author.is(AUTHOR_ID)).isTrue();
        assertThat(stranger.is(AUTHOR_ID)).isFalse();

        assertThat(admin.isAdmin()).isTrue();
        assertThat(teacher.isAdmin()).isFalse();

        assertThat(teacher.isTeacherOrAdmin()).isTrue();
        assertThat(admin.isTeacherOrAdmin()).isTrue();
        assertThat(ta.isTeacherOrAdmin()).isFalse();
        assertThat(author.isTeacherOrAdmin()).isFalse();
    }
}
