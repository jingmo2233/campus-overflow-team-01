package com.campusoverflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.identity.api.event.UserRegisteredEvent;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.InMemoryUsers;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.RecordingAudit;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.RecordingEvents;
import com.campusoverflow.identity.application.fake.IdentityTestFixtures.StubHasher;
import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 注册用例：唯一性约束、口令哈希、事件与审计。 */
class UserRegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");

    private final InMemoryUsers users = new InMemoryUsers();
    private final StubHasher hasher = new StubHasher();
    private final RecordingEvents events = new RecordingEvents();
    private final RecordingAudit audit = new RecordingAudit();
    private final UserRegistrationService service = new UserRegistrationService(users, hasher, events, audit,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void seedExistingUser() {
        service.register(new RegisterUserCommand("2025001", "已有同学", "exists@campus.edu.cn",
                "Campus2026", "计算机工程学院", "计科2301"));
    }

    @Test
    @DisplayName("注册成功：保存的是哈希而不是明文，并发布事件与审计")
    void registerHashesPasswordAndPublishesEvent() {
        long id = service.register(new RegisterUserCommand("2025002", "新同学", "new@campus.edu.cn",
                "Campus2026", "计算机工程学院", "计科2302"));

        var saved = users.findById(id).orElseThrow();
        assertThat(saved.passwordHash()).isNotEqualTo("Campus2026");
        assertThat(saved.passwordHash()).isEqualTo(hasher.hash("Campus2026"));
        assertThat(saved.role()).isEqualTo(Role.STUDENT);
        assertThat(saved.verified()).isFalse();

        List<UserRegisteredEvent> published = events.ofType(UserRegisteredEvent.class);
        assertThat(published).hasSize(2);
        assertThat(published.get(1).displayName()).isEqualTo("新同学");
        assertThat(published.get(1).role()).isEqualTo(Role.STUDENT);

        assertThat(audit.actions).contains("USER_REGISTERED");
    }

    @Test
    @DisplayName("审计记录里的学号被脱敏，不写入完整账号")
    void auditMasksUsername() {
        service.register(new RegisterUserCommand("2025003", "审计同学", "audit@campus.edu.cn",
                "Campus2026", null, null));

        String detail = audit.details.get(audit.details.size() - 1);
        assertThat(detail).isEqualTo("2025****03");
    }

    @Test
    @DisplayName("学号重复被拒绝")
    void duplicateUsernameRejected() {
        assertThatThrownBy(() -> service.register(new RegisterUserCommand("2025001", "另一个昵称",
                "other@campus.edu.cn", "Campus2026", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("学号");
    }

    @Test
    @DisplayName("昵称重复被拒绝")
    void duplicateDisplayNameRejected() {
        assertThatThrownBy(() -> service.register(new RegisterUserCommand("2025004", "已有同学",
                "another@campus.edu.cn", "Campus2026", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("昵称");
    }

    @Test
    @DisplayName("邮箱重复被拒绝（大小写不敏感）")
    void duplicateEmailRejectedIgnoringCase() {
        assertThatThrownBy(() -> service.register(new RegisterUserCommand("2025005", "邮箱同学",
                "EXISTS@campus.edu.cn", "Campus2026", null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("邮箱");
    }

    @Test
    @DisplayName("弱口令在进入领域层之前就被策略拦下")
    void weakPasswordRejected() {
        assertThatThrownBy(() -> service.register(new RegisterUserCommand("2025006", "弱口令",
                "weak@campus.edu.cn", "12345678", null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("字母和数字");
    }

    @Test
    @DisplayName("非法学号被领域层拒绝")
    void illegalUsernameRejected() {
        assertThatThrownBy(() -> service.register(new RegisterUserCommand("abc", "短学号",
                "short@campus.edu.cn", "Campus2026", null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("学号");
    }

    @Test
    @DisplayName("校验失败时不会写入任何用户")
    void failedRegistrationDoesNotPersist() {
        int before = users.saveCount;
        assertThatThrownBy(() -> service.register(new RegisterUserCommand("2025007", "半途而废",
                "exists@campus.edu.cn", "Campus2026", null, null)))
                .isInstanceOf(ConflictException.class);

        assertThat(users.saveCount).isEqualTo(before);
    }
}
