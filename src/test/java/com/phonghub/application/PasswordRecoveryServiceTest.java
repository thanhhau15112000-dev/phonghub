package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.phonghub.adapter.out.audit.InMemoryAuditAdapter;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryUserRepository;
import com.phonghub.application.port.in.NotificationUseCase;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.PasswordRecoveryPort;
import com.phonghub.application.service.PasswordRecoveryService;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.IdentityProviderUnavailableException;
import com.phonghub.domain.exception.InvalidPasswordRecoveryLinkException;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Quên mật khẩu tự phục vụ qua email (#330). */
class PasswordRecoveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");
    private static final String BASE_URL = "https://phonghub.example.com/";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryAuditAdapter audit = new InMemoryAuditAdapter();
    private final NotificationUseCase notifications = mock(NotificationUseCase.class);
    private final FakeIdentityProvider identity = new FakeIdentityProvider();
    private final FakeRecoveryPort recovery = new FakeRecoveryPort();
    private PasswordRecoveryService service;
    private User tenant;

    @BeforeEach
    void setUp() {
        tenant = users.save(user("tenant7", "tenant7@mail.test", UserRole.TENANT, User.UserStatus.ACTIVE, true));
        service = new PasswordRecoveryService(users, identity, recovery, notifications, audit, BASE_URL,
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static User user(String username, String email, UserRole role, User.UserStatus status, boolean mustChange) {
        return new User(UUID.randomUUID(), username, email, username, "0900000000", role, status, mustChange, NOW);
    }

    @Test
    @DisplayName("Có email: gửi liên kết về trang đặt lại của app, không báo Quản trị viên")
    void sendsRecoveryEmailWithAppRedirect() {
        service.requestRecovery("  TENANT7 ");
        assertEquals(List.of("tenant7@mail.test -> https://phonghub.example.com/reset-password"), recovery.sent);
        verify(notifications, never()).requestPasswordReset(any());

        service.requestRecovery("tenant7@mail.test");
        assertEquals(2, recovery.sent.size());
    }

    @Test
    @DisplayName("Không có tài khoản: không gửi gì, không lộ thông tin")
    void unknownAccountDoesNothing() {
        service.requestRecovery("khongco");
        service.requestRecovery("");
        assertTrue(recovery.sent.isEmpty());
        verify(notifications, never()).requestPasswordReset(any());
    }

    @Test
    @DisplayName("Gửi email lỗi (Supabase sập, vượt hạn mức email): quay về báo Quản trị viên")
    void fallsBackToAdminNotification() {
        recovery.failSend = true;
        service.requestRecovery("tenant7");
        verify(notifications).requestPasswordReset(tenant);
    }

    @Test
    @DisplayName("Tài khoản bị khóa không nhận email đặt lại; Quản trị viên quyết định")
    void lockedAccountGetsNoEmail() {
        User locked = users.save(user("locked1", "locked1@mail.test", UserRole.TENANT, User.UserStatus.SUSPENDED, false));
        service.requestRecovery("locked1");
        assertTrue(recovery.sent.isEmpty());
        verify(notifications).requestPasswordReset(locked);
    }

    @Test
    @DisplayName("Không cấu hình địa chỉ công khai: để Supabase dùng Site URL")
    void blankBaseUrlLetsProviderUseSiteUrl() {
        new PasswordRecoveryService(users, identity, recovery, notifications, audit, " ", Clock.systemUTC())
            .requestRecovery("tenant7");
        assertEquals(List.of("tenant7@mail.test -> null"), recovery.sent);
    }

    @Test
    @DisplayName("Token hợp lệ: đặt mật khẩu mới, bỏ cờ phải đổi mật khẩu, thu hồi phiên, ghi nhật ký")
    void resetsPasswordWithValidToken() {
        recovery.tokens.put("tok-1", new PasswordRecoveryPort.RecoveryToken(tenant.id(), "sess-1", NOW.plusSeconds(3600)));

        service.resetPassword("tok-1", "MatKhauMoi#2026", "MatKhauMoi#2026");

        assertEquals("MatKhauMoi#2026", identity.passwords.get(tenant.id()));
        assertFalse(users.findById(tenant.id()).orElseThrow().mustChangePassword());
        assertEquals(List.of("tok-1"), identity.loggedOut);
        assertEquals("USER_PASSWORD_RECOVERED", audit.getRecordedEvents().get(0).action());
        assertFalse(audit.getRecordedEvents().get(0).details().toString().contains("MatKhauMoi"));
    }

    @Test
    @DisplayName("Mỗi liên kết chỉ dùng được một lần")
    void linkIsSingleUse() {
        recovery.tokens.put("tok-1", new PasswordRecoveryPort.RecoveryToken(tenant.id(), "sess-1", NOW.plusSeconds(3600)));
        service.resetPassword("tok-1", "MatKhauMoi#2026", "MatKhauMoi#2026");
        assertThrows(InvalidPasswordRecoveryLinkException.class,
            () -> service.resetPassword("tok-1", "MatKhauKhac#2026", "MatKhauKhac#2026"));
        assertEquals("MatKhauMoi#2026", identity.passwords.get(tenant.id()));
    }

    @Test
    @DisplayName("Mật khẩu yếu bị nhà cung cấp từ chối: liên kết vẫn dùng lại được")
    void weakPasswordKeepsLinkUsable() {
        recovery.tokens.put("tok-1", new PasswordRecoveryPort.RecoveryToken(tenant.id(), "sess-1", NOW.plusSeconds(3600)));
        identity.rejectPassword = "weakweak";
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("tok-1", "weakweak", "weakweak"));
        service.resetPassword("tok-1", "MatKhauMoi#2026", "MatKhauMoi#2026");
        assertEquals("MatKhauMoi#2026", identity.passwords.get(tenant.id()));
    }

    @Test
    @DisplayName("Nhập sai: mật khẩu ngắn, không khớp, thiếu token, token sai")
    void rejectsBadInput() {
        recovery.tokens.put("tok-1", new PasswordRecoveryPort.RecoveryToken(tenant.id(), "sess-1", NOW.plusSeconds(3600)));
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("tok-1", "ngan", "ngan"));
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("tok-1", "MatKhauMoi#2026", "khac#2026xx"));
        assertThrows(InvalidPasswordRecoveryLinkException.class, () -> service.resetPassword(" ", "MatKhauMoi#2026", "MatKhauMoi#2026"));
        assertThrows(InvalidPasswordRecoveryLinkException.class, () -> service.resetPassword("gia-mao", "MatKhauMoi#2026", "MatKhauMoi#2026"));
        assertTrue(identity.passwords.isEmpty());
    }

    @Test
    @DisplayName("Tài khoản bị khóa hoặc đã xóa: không đặt lại được bằng liên kết")
    void lockedOrMissingAccountCannotReset() {
        User locked = users.save(user("locked1", "locked1@mail.test", UserRole.TENANT, User.UserStatus.SUSPENDED, false));
        recovery.tokens.put("tok-locked", new PasswordRecoveryPort.RecoveryToken(locked.id(), "s-l", NOW.plusSeconds(60)));
        recovery.tokens.put("tok-gone", new PasswordRecoveryPort.RecoveryToken(UUID.randomUUID(), "s-g", NOW.plusSeconds(60)));
        assertThrows(InvalidPasswordRecoveryLinkException.class,
            () -> service.resetPassword("tok-locked", "MatKhauMoi#2026", "MatKhauMoi#2026"));
        assertThrows(InvalidPasswordRecoveryLinkException.class,
            () -> service.resetPassword("tok-gone", "MatKhauMoi#2026", "MatKhauMoi#2026"));
        assertTrue(identity.passwords.isEmpty());
    }

    @Test
    @DisplayName("Chạy không có Supabase: chỉ báo Quản trị viên, trang đặt lại báo chưa bật")
    void withoutProviderFallsBackToAdmin() {
        PasswordRecoveryService demo = new PasswordRecoveryService(users, identity, null, notifications, audit, BASE_URL, null);
        assertFalse(demo.isEmailRecoveryAvailable());
        demo.requestRecovery("tenant7");
        verify(notifications).requestPasswordReset(tenant);
        assertThrows(DomainException.class, () -> demo.resetPassword("tok", "MatKhauMoi#2026", "MatKhauMoi#2026"));
    }

    @Test
    @DisplayName("Quản trị viên gửi email lỗi: không tạo thông báo cho chính Quản trị viên")
    void adminIsNotNotifiedAboutThemselves() {
        users.save(user("admin2", "admin2@mail.test", UserRole.ADMIN, User.UserStatus.ACTIVE, false));
        recovery.failSend = true;
        service.requestRecovery("admin2");
        verify(notifications, never()).requestPasswordReset(any());
    }

    @Test
    @DisplayName("Thu hồi phiên lỗi không làm hỏng việc đặt lại mật khẩu")
    void logoutFailureIsIgnored() {
        recovery.tokens.put("tok-1", new PasswordRecoveryPort.RecoveryToken(tenant.id(), null, null));
        identity.failLogout = true;
        service.resetPassword("tok-1", "MatKhauMoi#2026", "MatKhauMoi#2026");
        assertEquals("MatKhauMoi#2026", identity.passwords.get(tenant.id()));
        assertFalse(users.findById(tenant.id()).orElseThrow().mustChangePassword());
    }

    private static final class FakeRecoveryPort implements PasswordRecoveryPort {
        final List<String> sent = new ArrayList<>();
        final Map<String, RecoveryToken> tokens = new HashMap<>();
        boolean failSend;

        @Override
        public void sendRecoveryEmail(String email, String redirectTo) {
            if (failSend) {
                throw new IdentityProviderUnavailableException("down");
            }
            sent.add(email + " -> " + redirectTo);
        }

        @Override
        public RecoveryToken verifyRecoveryToken(String token) {
            RecoveryToken found = tokens.get(token);
            if (found == null) {
                throw new InvalidPasswordRecoveryLinkException();
            }
            return found;
        }
    }

    private static final class FakeIdentityProvider implements IdentityProviderPort {
        final Map<UUID, String> passwords = new HashMap<>();
        final List<String> loggedOut = new ArrayList<>();
        String rejectPassword;
        boolean failLogout;

        @Override public RawTokenResponse login(String email, String password) { throw new UnsupportedOperationException(); }
        @Override public RawTokenResponse refreshToken(String refreshToken) { throw new UnsupportedOperationException(); }
        @Override public void logout(String accessToken) {
            if (failLogout) {
                throw new IdentityProviderUnavailableException("down");
            }
            loggedOut.add(accessToken);
        }
        @Override public void changePassword(UUID userId, String newPassword) { adminSetPassword(userId, newPassword); }
        @Override public void adminSetPassword(UUID userId, String password) {
            if (password.equals(rejectPassword)) {
                throw new IllegalArgumentException("Mật khẩu không đủ độ mạnh");
            }
            passwords.put(userId, password);
        }
        @Override public UUID adminCreateUser(String email, String temporaryPassword) { throw new UnsupportedOperationException(); }
        @Override public void adminDeleteUser(UUID userId) { throw new UnsupportedOperationException(); }
    }
}
