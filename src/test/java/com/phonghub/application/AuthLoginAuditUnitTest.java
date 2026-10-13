package com.phonghub.application;

import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.application.service.AuthService;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.domain.exception.AccountDisabledException;
import com.phonghub.domain.exception.InvalidCredentialsException;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** #78: đăng nhập thành công/thất bại được ghi lại mà không lộ mật khẩu hay tên gõ nhầm. */
class AuthLoginAuditUnitTest {

    private final List<AuditPort.AuditEvent> events = new ArrayList<>();
    private UserRepositoryPort userRepository;
    private IdentityProviderPort identityProvider;
    private AuthService authService;
    private User owner;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepositoryPort.class);
        identityProvider = mock(IdentityProviderPort.class);
        authService = new AuthService(userRepository, identityProvider, mock(CurrentUserPort.class),
            mock(AuthorizationService.class), events::add);
        owner = new User(UUID.randomUUID(), "owner1", "owner1@phonghub.local", "Chủ trọ", "0900000001",
            UserRole.OWNER, User.UserStatus.ACTIVE, false, Instant.now());
    }

    @Test
    @DisplayName("đăng nhập đúng ghi LOGIN_SUCCEEDED cho đúng người dùng")
    void successIsRecorded() {
        when(userRepository.findByUsername("owner1")).thenReturn(Optional.of(owner));
        when(identityProvider.login("owner1@phonghub.local", "right-pass"))
            .thenReturn(new IdentityProviderPort.RawTokenResponse("a", "r", "Bearer", 60L));

        authService.login("owner1", "right-pass");

        assertEquals(1, events.size());
        AuditPort.AuditEvent event = events.getFirst();
        assertEquals("LOGIN_SUCCEEDED", event.action());
        assertEquals(owner.id(), event.actorId());
        assertEquals(owner.id().toString(), event.targetId());
    }

    @Test
    @DisplayName("sai mật khẩu ghi LOGIN_FAILED, không chứa mật khẩu")
    void wrongPasswordIsRecordedWithoutPassword() {
        when(userRepository.findByUsername("owner1")).thenReturn(Optional.of(owner));
        when(identityProvider.login("owner1@phonghub.local", "wrong-pass-123"))
            .thenThrow(new InvalidCredentialsException("bad"));

        assertThrows(InvalidCredentialsException.class, () -> authService.login("owner1", "wrong-pass-123"));

        AuditPort.AuditEvent event = events.getFirst();
        assertEquals("LOGIN_FAILED", event.action());
        assertNull(event.actorId());
        assertEquals(owner.id().toString(), event.targetId());
        assertEquals("INVALID_CREDENTIALS", event.details().get("reason"));
        assertFalse(event.details().toString().contains("wrong-pass-123"));
    }

    @Test
    @DisplayName("tên không tồn tại chỉ lưu bản băm ngắn, không lưu nguyên văn (có thể là mật khẩu gõ nhầm)")
    void unknownUsernameIsHashed() {
        when(userRepository.findByUsername("mypassword99")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login("MyPassword99", "x"));

        AuditPort.AuditEvent event = events.getFirst();
        assertEquals("UNKNOWN_USER", event.details().get("reason"));
        assertNull(event.targetId());
        assertFalse(event.details().toString().toLowerCase().contains("mypassword99"));
        assertEquals(8, String.valueOf(event.details().get("identifierHash")).length());
    }

    @Test
    @DisplayName("tài khoản bị khóa ghi LOGIN_FAILED/ACCOUNT_DISABLED")
    void disabledAccountIsRecorded() {
        User locked = new User(UUID.randomUUID(), "locked", "locked@phonghub.local", "Khóa", "0900000002",
            UserRole.TENANT, User.UserStatus.SUSPENDED, false, Instant.now());
        when(userRepository.findByUsername("locked")).thenReturn(Optional.of(locked));

        assertThrows(AccountDisabledException.class, () -> authService.login("locked", "x"));

        assertEquals("ACCOUNT_DISABLED", events.getFirst().details().get("reason"));
        assertTrue(events.stream().allMatch(e -> e.action().equals("LOGIN_FAILED")));
    }
}
