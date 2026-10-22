package com.campusoverflow.shared.security;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发起当前操作的主体。由接口层从安全上下文中解析后显式传入应用服务，
 * 应用层与领域层因此不依赖 Spring Security，也便于单元测试构造任意身份。
 */
public record Actor(long userId, String displayName, Role role) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isTeacherOrAdmin() {
        return role == Role.TEACHER || role == Role.ADMIN;
    }

    public boolean is(long otherUserId) {
        return userId == otherUserId;
    }

    /**
     * 是否为「内容的可管理者」：作者本人，或对该内容所属课程有管理权限（教师/助教/管理员）。
     * 课程管理权限由应用层通过 Identity 上下文的课程成员关系计算后传入，领域层不查库。
     * 各上下文统一走这里，避免同一段权限判断被复制多份后遗漏更新。
     */
    public boolean canManage(long contentAuthorId, boolean managesCourse) {
        return userId == contentAuthorId || managesCourse;
    }
}
