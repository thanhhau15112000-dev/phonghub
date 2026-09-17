package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phonghub.application.port.in.AuthTokenResponse;
import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.application.port.out.CurrentUser;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceUnitTest {

    private UserRepositoryPort userRepository;
    private IdentityProviderPort identityProviderPort;
    private CurrentUserPort currentUserPort;
    private AuthorizationService authorizationService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepositoryPort.class);
        identityProviderPort = mock(IdentityProviderPort.class);
        currentUserPort = mock(CurrentUserPort.class);
        authorizationService = mock(AuthorizationService.class);
        authService = new AuthService(userRepository, identityProviderPort, currentUserPort, authorizationService);
    }

    @Test
    void loginMapsUsernameToHiddenEmailAndReturnsTokens() {
        UUID userId = UUID.randomUUID();
        User activeUser = new User(
            userId, "tenant1", "tenant1@phonghub.local", "Nguyen Van A", "0901234567",
            UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now()
        );

        when(userRepository.findByUsername("tenant1")).thenReturn(Optional.of(activeUser));
        when(identityProviderPort.login("tenant1@phonghub.local", "secret123"))
            .thenReturn(new IdentityProviderPort.RawTokenResponse("jwt-access-token", "jwt-refresh-token", "Bearer", 3600L));

        AuthTokenResponse response = authService.login("tenant1", "secret123");

        assertNotNull(response);
        assertEquals("jwt-access-token", response.accessToken());
        assertEquals("jwt-refresh-token", response.refreshToken());
        assertNotNull(response.user());
        assertFalse(response.user().mustChangePassword());
        assertEquals(userId, response.user().id());
    }

    @Test
    void loginReturnsMustChangePasswordTrueWhenUserRequiresPasswordChange() {
        UUID userId = UUID.randomUUID();
        User userMustChange = new User(
            userId, "staff1", "staff1@phonghub.local", "Tran Thi B", "0912345678",
            UserRole.STAFF, User.UserStatus.ACTIVE, true, Instant.now()
        );

        when(userRepository.findByUsername("staff1")).thenReturn(Optional.of(userMustChange));
        when(identityProviderPort.login("staff1@phonghub.local", "tempPass123"))
            .thenReturn(new IdentityProviderPort.RawTokenResponse("jwt-access-token", "jwt-refresh-token", "Bearer", 3600L));

        AuthTokenResponse response = authService.login("staff1", "tempPass123");

        assertNotNull(response);
        assertTrue(response.user().mustChangePassword(), "Response must indicate that password change is required");
    }

    @Test
    void loginRejectsInactiveUser() {
        UUID userId = UUID.randomUUID();
        User suspendedUser = new User(
            userId, "tenant2", "tenant2@phonghub.local", "Le Van C", "0923456789",
            UserRole.TENANT, User.UserStatus.SUSPENDED, false, Instant.now()
        );

        when(userRepository.findByUsername("tenant2")).thenReturn(Optional.of(suspendedUser));

        assertThrows(
            AccountDisabledException.class,
            () -> authService.login("tenant2", "secret123")
        );
        verify(identityProviderPort, never()).login(anyString(), anyString());
    }

    @Test
    void loginRejectsUnknownUsername() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(
            InvalidCredentialsException.class,
            () -> authService.login("unknown", "secret123")
        );
    }

    @Test
    void refreshTokenDoesNotRequireAnUnexpiredAccessToken() {
        when(identityProviderPort.refreshToken("refresh-token"))
            .thenReturn(new IdentityProviderPort.RawTokenResponse(
                "new-access-token", "new-refresh-token", "Bearer", 3600L
            ));

        AuthTokenResponse response = authService.refreshToken("refresh-token");

        assertEquals("new-access-token", response.accessToken());
        assertEquals("new-refresh-token", response.refreshToken());
        assertNull(response.user());
        verify(currentUserPort, never()).getCurrentUser();
    }

    @Test
    void adminResetPasswordGenerates16CharTempPasswordAndUpdatesMustChangePassword() {
        UUID adminId = UUID.randomUUID();
        CurrentUser adminActor = new CurrentUser(adminId, "admin@phonghub.local", "Admin", UserRole.ADMIN);
        when(currentUserPort.getCurrentUser()).thenReturn(adminActor);

        UUID tenantUserId = UUID.randomUUID();
        User tenantUser = new User(
            tenantUserId, "tenant1", "tenant1@phonghub.local", "Nguyen Van A", "0901234567",
            UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now()
        );
        when(userRepository.findById(tenantUserId)).thenReturn(Optional.of(tenantUser));

        AuthUseCase.PasswordResetResult result = authService.adminResetPassword(tenantUserId);

        assertNotNull(result);
        assertEquals(tenantUserId, result.userId());
        assertNotNull(result.temporaryPassword());
        assertEquals(16, result.temporaryPassword().length(), "Temporary password must be 16 characters long");
        assertTrue(result.mustChangePassword());
        verify(identityProviderPort).adminSetPassword(eq(tenantUserId), eq(result.temporaryPassword()));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void adminResetPasswordRefusesToResetAdminUser() {
        UUID adminId = UUID.randomUUID();
        CurrentUser adminActor = new CurrentUser(adminId, "admin@phonghub.local", "Admin", UserRole.ADMIN);
        when(currentUserPort.getCurrentUser()).thenReturn(adminActor);

        UUID targetAdminId = UUID.randomUUID();
        User targetAdminUser = new User(
            targetAdminId, "admin2", "admin2@phonghub.local", "Admin Two", "0987654321",
            UserRole.ADMIN, User.UserStatus.ACTIVE, false, Instant.now()
        );
        when(userRepository.findById(targetAdminId)).thenReturn(Optional.of(targetAdminUser));

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> authService.adminResetPassword(targetAdminId)
        );

        assertTrue(ex.getMessage().contains("ADMIN accounts"));
        verify(identityProviderPort, never()).adminSetPassword(any(), any());
    }
}
