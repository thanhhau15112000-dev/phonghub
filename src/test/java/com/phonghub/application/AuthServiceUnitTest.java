package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phonghub.application.port.in.AuthTokenResponse;
import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.application.service.AuthService;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.domain.exception.AccountDisabledException;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.exception.DuplicateEmailException;
import com.phonghub.domain.exception.DuplicateUsernameException;
import com.phonghub.domain.exception.IdentityProviderUnavailableException;
import com.phonghub.domain.exception.InvalidCredentialsException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import com.phonghub.domain.model.Tenant;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceUnitTest {

    private UserRepositoryPort userRepository;
    private IdentityProviderPort identityProviderPort;
    private CurrentUserPort currentUserPort;
    private AuthorizationService authorizationService;
    private AuditPort auditPort;
    private TenantRepositoryPort tenantRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepositoryPort.class);
        identityProviderPort = mock(IdentityProviderPort.class);
        currentUserPort = mock(CurrentUserPort.class);
        authorizationService = mock(AuthorizationService.class);
        auditPort = mock(AuditPort.class);
        tenantRepository = mock(TenantRepositoryPort.class);
        authService = new AuthService(userRepository, identityProviderPort, currentUserPort, authorizationService,
            auditPort, tenantRepository);
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
        // Supabase login được gọi TRƯỚC khi check status — password đúng thì mới kiểm tra trạng thái tài khoản
        when(identityProviderPort.login("tenant2@phonghub.local", "secret123"))
            .thenReturn(new IdentityProviderPort.RawTokenResponse("tok", "ref", "Bearer", 3600));

        assertThrows(
            AccountDisabledException.class,
            () -> authService.login("tenant2", "secret123")
        );
        // Supabase PHẢI được gọi (password được xác thực trước khi biết account bị khoá)
        verify(identityProviderPort).login("tenant2@phonghub.local", "secret123");
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

    @Test
    void adminDeleteUserSucceedsForNonAdminUser() {
        UUID adminId = UUID.randomUUID();
        CurrentUser adminActor = new CurrentUser(adminId, "admin@phonghub.local", "Admin", UserRole.ADMIN);
        when(currentUserPort.getCurrentUser()).thenReturn(adminActor);

        UUID tenantUserId = UUID.randomUUID();
        User tenantUser = new User(
            tenantUserId, "tenant1", "tenant1@phonghub.local", "Nguyen Van A", "0901234567",
            UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now()
        );
        when(userRepository.findById(tenantUserId)).thenReturn(Optional.of(tenantUser));

        authService.adminDeleteUser(tenantUserId);

        verify(identityProviderPort).adminDeleteUser(tenantUserId);
        verify(userRepository).deleteById(tenantUserId);
        verify(auditPort).recordEvent(any(AuditPort.AuditEvent.class));
    }

    @Test
    void adminDeleteUserRefusesSelfDeletion() {
        UUID adminId = UUID.randomUUID();
        CurrentUser adminActor = new CurrentUser(adminId, "admin@phonghub.local", "Admin", UserRole.ADMIN);
        when(currentUserPort.getCurrentUser()).thenReturn(adminActor);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> authService.adminDeleteUser(adminId)
        );

        assertTrue(ex.getMessage().contains("tự xoá tài khoản"));
        verify(identityProviderPort, never()).adminDeleteUser(any());
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void adminDeleteUserRefusesToDeleteAnotherAdmin() {
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
            () -> authService.adminDeleteUser(targetAdminId)
        );

        assertTrue(ex.getMessage().contains("ADMIN"));
        verify(identityProviderPort, never()).adminDeleteUser(any());
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void adminDeleteUserAbortsIfSupabaseFails() {
        UUID adminId = UUID.randomUUID();
        CurrentUser adminActor = new CurrentUser(adminId, "admin@phonghub.local", "Admin", UserRole.ADMIN);
        when(currentUserPort.getCurrentUser()).thenReturn(adminActor);

        UUID staffUserId = UUID.randomUUID();
        User staffUser = new User(
            staffUserId, "staff1", "staff1@phonghub.local", "Staff Member", "0911223344",
            UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()
        );
        when(userRepository.findById(staffUserId)).thenReturn(Optional.of(staffUser));
        org.mockito.Mockito.doThrow(new IdentityProviderUnavailableException("Supabase service error"))
            .when(identityProviderPort).adminDeleteUser(staffUserId);

        assertThrows(
            IdentityProviderUnavailableException.class,
            () -> authService.adminDeleteUser(staffUserId)
        );

        verify(identityProviderPort).adminDeleteUser(staffUserId);
        verify(userRepository, never()).deleteById(any());
        verify(auditPort, never()).recordEvent(any());
    }

    @Test
    void adminDeleteUserThrowsForbiddenForNonAdminCaller() {
        UUID staffId = UUID.randomUUID();
        CurrentUser staffActor = new CurrentUser(staffId, "staff@phonghub.local", "Staff", UserRole.STAFF);
        when(currentUserPort.getCurrentUser()).thenReturn(staffActor);
        org.mockito.Mockito.doThrow(new UnauthorizedPropertyAccessException("Action requires ADMIN role"))
            .when(authorizationService).assertAdmin(staffActor);

        UUID targetId = UUID.randomUUID();
        assertThrows(
            UnauthorizedPropertyAccessException.class,
            () -> authService.adminDeleteUser(targetId)
        );

        verify(identityProviderPort, never()).adminDeleteUser(any());
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void adminCreateUserRejectsDuplicateEmailCaseInsensitive() {
        UUID adminId = UUID.randomUUID();
        CurrentUser adminActor = new CurrentUser(adminId, "admin@phonghub.local", "Admin", UserRole.ADMIN);
        when(currentUserPort.getCurrentUser()).thenReturn(adminActor);

        User existing = new User(
            UUID.randomUUID(), "other", "tenant1@phonghub.local", "Existing", "0900000000",
            UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now()
        );
        when(userRepository.findByEmail("tenant1@phonghub.local")).thenReturn(Optional.of(existing));

        AuthUseCase.CreateUserCommand cmd = new AuthUseCase.CreateUserCommand(
            "newuser", "Tenant1@PhongHub.Local", "New User", "0911223344", UserRole.TENANT
        );

        assertThrows(
            DuplicateEmailException.class,
            () -> authService.adminCreateUser(cmd)
        );

        verify(identityProviderPort, never()).adminCreateUser(any(), any());
    }

    @Test
    void adminCreateUserRejectsDuplicateUsernameCaseInsensitive() {
        UUID adminId = UUID.randomUUID();
        CurrentUser adminActor = new CurrentUser(adminId, "admin@phonghub.local", "Admin", UserRole.ADMIN);
        when(currentUserPort.getCurrentUser()).thenReturn(adminActor);

        when(userRepository.findByEmail("new@phonghub.local")).thenReturn(Optional.empty());

        User existing = new User(
            UUID.randomUUID(), "tenant1", "other@phonghub.local", "Existing", "0900000000",
            UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now()
        );
        when(userRepository.findByUsername("tenant1")).thenReturn(Optional.of(existing));

        AuthUseCase.CreateUserCommand cmd = new AuthUseCase.CreateUserCommand(
            "Tenant1", "new@phonghub.local", "New User", "0911223344", UserRole.TENANT
        );

        assertThrows(
            DuplicateUsernameException.class,
            () -> authService.adminCreateUser(cmd)
        );

        verify(identityProviderPort, never()).adminCreateUser(any(), any());
    }

    @Test
    void adminCreateUserRejectsInvalidEmailFormat() {
        UUID adminId = UUID.randomUUID();
        CurrentUser adminActor = new CurrentUser(adminId, "admin@phonghub.local", "Admin", UserRole.ADMIN);
        when(currentUserPort.getCurrentUser()).thenReturn(adminActor);

        AuthUseCase.CreateUserCommand cmd = new AuthUseCase.CreateUserCommand(
            "validuser", "invalid-email-string", "Valid User", "0911223344", UserRole.TENANT
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> authService.adminCreateUser(cmd)
        );

        verify(identityProviderPort, never()).adminCreateUser(any(), any());
    }

    @Test
    void adminCreateUserNormalizesEmailAndUsernameToLowercase() {
        UUID adminId = UUID.randomUUID();
        CurrentUser adminActor = new CurrentUser(adminId, "admin@phonghub.local", "Admin", UserRole.ADMIN);
        when(currentUserPort.getCurrentUser()).thenReturn(adminActor);

        when(userRepository.findByEmail("mixedcase@phonghub.local")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("mixedcaseuser")).thenReturn(Optional.empty());

        UUID newAuthId = UUID.randomUUID();
        when(identityProviderPort.adminCreateUser(eq("mixedcase@phonghub.local"), any())).thenReturn(newAuthId);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthUseCase.CreateUserCommand cmd = new AuthUseCase.CreateUserCommand(
            "  MixedCaseUser  ", "  MixedCase@PhongHub.Local  ", "Mixed Case", "0911223344", UserRole.STAFF
        );

        AuthUseCase.AdminCreateUserResult result = authService.adminCreateUser(cmd);

        assertEquals("mixedcaseuser", result.username());
        assertEquals("mixedcase@phonghub.local", result.email());
        verify(identityProviderPort).adminCreateUser(eq("mixedcase@phonghub.local"), any());
        verify(userRepository).save(argThat(u ->
            u.username().equals("mixedcaseuser") && u.email().equals("mixedcase@phonghub.local")
        ));
    }

    @Test
    void adminCreateUserRejectsDuplicatePhone() {
        UUID adminId = UUID.randomUUID();
        CurrentUser adminActor = new CurrentUser(adminId, "admin@phonghub.local", "Admin", UserRole.ADMIN);
        when(currentUserPort.getCurrentUser()).thenReturn(adminActor);

        when(userRepository.findByEmail("new@phonghub.local")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());

        User existing = new User(
            UUID.randomUUID(), "other", "other@phonghub.local", "Other", "0911223344",
            UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now()
        );
        when(userRepository.findAll()).thenReturn(java.util.List.of(existing));

        AuthUseCase.CreateUserCommand cmd = new AuthUseCase.CreateUserCommand(
            "newuser", "new@phonghub.local", "New User", "0911223344", UserRole.TENANT
        );

        assertThrows(
            DomainException.class,
            () -> authService.adminCreateUser(cmd)
        );

        verify(identityProviderPort, never()).adminCreateUser(any(), any());
    }

    @Test
    void adminCreateTenantCreatesCccdProfileLinkedToTheNewAccount() {
        CurrentUser adminActor = new CurrentUser(UUID.randomUUID(), "admin@phonghub.local", "Admin", UserRole.ADMIN);
        when(currentUserPort.getCurrentUser()).thenReturn(adminActor);
        when(userRepository.findByEmail("tenant@phonghub.local")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("tenant1")).thenReturn(Optional.empty());
        when(userRepository.findAll()).thenReturn(List.of());
        when(tenantRepository.findByIdentityCardNumber("123456789")).thenReturn(Optional.empty());
        UUID identityId = UUID.randomUUID();
        when(identityProviderPort.adminCreateUser(eq("tenant@phonghub.local"), any())).thenReturn(identityId);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(call -> call.getArgument(0));

        AuthUseCase.AdminCreateUserResult result = authService.adminCreateUser(new AuthUseCase.CreateUserCommand(
            "tenant1", "tenant@phonghub.local", "Tenant One", "0911223344", UserRole.TENANT, "123456789"));

        assertEquals(identityId, result.id());
        verify(tenantRepository).save(argThat(tenant -> tenant.userId().equals(identityId)
            && tenant.identityCardNumber().equals("123456789")
            && tenant.phone().equals("0911223344")
            && tenant.email().equals("tenant@phonghub.local")));
    }

    @Test
    void adminCreateTenantRejectsMissingCccdBeforeCreatingExternalIdentity() {
        when(currentUserPort.getCurrentUser()).thenReturn(
            new CurrentUser(UUID.randomUUID(), "admin@phonghub.local", "Admin", UserRole.ADMIN));

        assertThrows(IllegalArgumentException.class, () -> authService.adminCreateUser(new AuthUseCase.CreateUserCommand(
            "tenant1", "tenant@phonghub.local", "Tenant One", "0911223344", UserRole.TENANT)));

        verify(identityProviderPort, never()).adminCreateUser(any(), any());
    }

    @Test
    void adminCreateTenantCompensatesExternalIdentityWhenProfileSaveFails() {
        when(currentUserPort.getCurrentUser()).thenReturn(
            new CurrentUser(UUID.randomUUID(), "admin@phonghub.local", "Admin", UserRole.ADMIN));
        when(userRepository.findByEmail("tenant@phonghub.local")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("tenant1")).thenReturn(Optional.empty());
        when(userRepository.findAll()).thenReturn(List.of());
        when(tenantRepository.findByIdentityCardNumber("123456789")).thenReturn(Optional.empty());
        UUID identityId = UUID.randomUUID();
        when(identityProviderPort.adminCreateUser(eq("tenant@phonghub.local"), any())).thenReturn(identityId);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(tenantRepository.save(any(Tenant.class))).thenThrow(new RuntimeException("database unavailable"));

        assertThrows(DomainException.class, () -> authService.adminCreateUser(new AuthUseCase.CreateUserCommand(
            "tenant1", "tenant@phonghub.local", "Tenant One", "0911223344", UserRole.TENANT, "123456789")));

        verify(userRepository).deleteById(identityId);
        verify(identityProviderPort).adminDeleteUser(identityId);
    }

    @Test
    void adminCreateTenantCanAttachAnExistingUnlinkedProfile() {
        CurrentUser adminActor = new CurrentUser(UUID.randomUUID(), "admin@phonghub.local", "Admin", UserRole.ADMIN);
        UUID tenantId = UUID.randomUUID();
        Tenant unlinked = Tenant.create(null, "Existing Tenant", "123456789", "0901111222", null, "Address");
        when(currentUserPort.getCurrentUser()).thenReturn(adminActor);
        when(userRepository.findByEmail("tenant@phonghub.local")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("tenant1")).thenReturn(Optional.empty());
        when(userRepository.findAll()).thenReturn(List.of());
        when(tenantRepository.findByIdentityCardNumber("123456789")).thenReturn(Optional.of(unlinked));
        when(identityProviderPort.adminCreateUser(eq("tenant@phonghub.local"), any())).thenReturn(tenantId);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(call -> call.getArgument(0));

        authService.adminCreateUser(new AuthUseCase.CreateUserCommand(
            "tenant1", "tenant@phonghub.local", "Updated Name", "0911223344", UserRole.TENANT, "123456789"));

        verify(tenantRepository).save(argThat(saved -> saved.id().equals(unlinked.id())
            && saved.userId().equals(tenantId) && saved.identityCardNumber().equals("123456789")));
    }

    @Test
    void adminCreateTenantRejectsCccdAlreadyLinkedToAnotherAccount() {
        when(currentUserPort.getCurrentUser()).thenReturn(
            new CurrentUser(UUID.randomUUID(), "admin@phonghub.local", "Admin", UserRole.ADMIN));
        when(userRepository.findByEmail("tenant@phonghub.local")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("tenant1")).thenReturn(Optional.empty());
        when(userRepository.findAll()).thenReturn(List.of());
        Tenant linked = Tenant.create(UUID.randomUUID(), "Other Tenant", "123456789", "0901111222", null, null);
        when(tenantRepository.findByIdentityCardNumber("123456789")).thenReturn(Optional.of(linked));

        assertThrows(com.phonghub.domain.exception.DuplicateIdentityCardException.class,
            () -> authService.adminCreateUser(new AuthUseCase.CreateUserCommand(
                "tenant1", "tenant@phonghub.local", "Tenant One", "0911223344", UserRole.TENANT, "123456789")));

        verify(identityProviderPort, never()).adminCreateUser(any(), any());
    }

    @Test
    void legacyTenantAccountCanBeLinkedToProfileWithCccd() {
        UUID targetId = UUID.randomUUID();
        CurrentUser adminActor = new CurrentUser(UUID.randomUUID(), "admin@phonghub.local", "Admin", UserRole.ADMIN);
        User legacyUser = new User(targetId, "legacytenant", "legacy@phonghub.local", "Legacy Tenant", null,
            UserRole.TENANT, User.UserStatus.ACTIVE, true, Instant.now());
        when(currentUserPort.getCurrentUser()).thenReturn(adminActor);
        when(userRepository.findById(targetId)).thenReturn(Optional.of(legacyUser));
        when(userRepository.findAll()).thenReturn(List.of(legacyUser));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(tenantRepository.findByUserId(targetId)).thenReturn(Optional.empty());
        when(tenantRepository.findByIdentityCardNumber("123456789")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(call -> call.getArgument(0));

        authService.adminAttachTenantProfile(targetId, "123456789", "0911223344");

        verify(userRepository).save(argThat(user -> user.id().equals(targetId) && user.phone().equals("0911223344")));
        verify(tenantRepository).save(argThat(tenant -> tenant.userId().equals(targetId)
            && tenant.identityCardNumber().equals("123456789")
            && tenant.phone().equals("0911223344")));
    }

    @Test
    void tenantAccountsWithoutProfilesAreReturnedOnlyToAdmin() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser adminActor = new CurrentUser(UUID.randomUUID(), "admin@phonghub.local", "Admin", UserRole.ADMIN);
        User tenant = new User(tenantId, "tenant", "tenant@phonghub.local", "Tenant", "0911223344",
            UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now());
        when(currentUserPort.getCurrentUser()).thenReturn(adminActor);
        when(userRepository.findByRole(UserRole.TENANT)).thenReturn(List.of(tenant));
        when(tenantRepository.findByUserId(tenantId)).thenReturn(Optional.empty());

        assertEquals(List.of(tenant), authService.listTenantAccountsMissingProfiles());

        org.mockito.Mockito.doThrow(new UnauthorizedPropertyAccessException("admin only"))
            .when(authorizationService).assertAdmin(null);
        when(currentUserPort.getCurrentUser()).thenReturn(null);
        assertThrows(UnauthorizedPropertyAccessException.class, authService::listTenantAccountsMissingProfiles);
    }

    @Test
    void nonAdminCannotAttachTenantProfile() {
        CurrentUser owner = new CurrentUser(UUID.randomUUID(), "owner@phonghub.local", "Owner", UserRole.OWNER);
        when(currentUserPort.getCurrentUser()).thenReturn(owner);
        org.mockito.Mockito.doThrow(new UnauthorizedPropertyAccessException("admin only"))
            .when(authorizationService).assertAdmin(owner);

        assertThrows(UnauthorizedPropertyAccessException.class,
            () -> authService.adminAttachTenantProfile(UUID.randomUUID(), "123456789", "0911223344"));

        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    void loginInactiveUserWithWrongPasswordThrowsInvalidCredentials() {
        UUID userId = UUID.randomUUID();
        User suspendedUser = new User(
            userId, "suspended_user", "suspended@phonghub.local", "Suspended User", "0923456789",
            UserRole.TENANT, User.UserStatus.SUSPENDED, false, Instant.now()
        );

        when(userRepository.findByUsername("suspended_user")).thenReturn(Optional.of(suspendedUser));
        when(identityProviderPort.login("suspended@phonghub.local", "wrong-password"))
            .thenThrow(new InvalidCredentialsException("Invalid username or password"));

        // Phải ném InvalidCredentialsException, KHÔNG tiết lộ tài khoản bị khóa
        assertThrows(
            InvalidCredentialsException.class,
            () -> authService.login("suspended_user", "wrong-password")
        );
        verify(identityProviderPort).login("suspended@phonghub.local", "wrong-password");
    }

    @Test
    void adminCreateUserRejectsInvalidUsernameFormat() {
        UUID adminId = UUID.randomUUID();
        CurrentUser adminActor = new CurrentUser(adminId, "admin@phonghub.local", "Admin", UserRole.ADMIN);
        when(currentUserPort.getCurrentUser()).thenReturn(adminActor);

        // Chứa ký tự đặc biệt XSS hoặc HTML
        assertThrows(IllegalArgumentException.class, () -> authService.adminCreateUser(
            new AuthUseCase.CreateUserCommand("<script>", "xss@phonghub.local", "XSS", "0911223344", UserRole.TENANT)
        ));

        // Chứa ký tự '@'
        assertThrows(IllegalArgumentException.class, () -> authService.adminCreateUser(
            new AuthUseCase.CreateUserCommand("user@phonghub", "at@phonghub.local", "At", "0911223344", UserRole.TENANT)
        ));

        // Quá ngắn (< 3 ký tự)
        assertThrows(IllegalArgumentException.class, () -> authService.adminCreateUser(
            new AuthUseCase.CreateUserCommand("ab", "short@phonghub.local", "Short", "0911223344", UserRole.TENANT)
        ));

        // Chứa khoảng trắng
        assertThrows(IllegalArgumentException.class, () -> authService.adminCreateUser(
            new AuthUseCase.CreateUserCommand("invalid user", "space@phonghub.local", "Space", "0911223344", UserRole.TENANT)
        ));
    }

    @Test
    void temporaryPasswordContainsAllFourCharacterClasses() {
        for (int i = 0; i < 50; i++) {
            String tempPass = AuthService.generateSecureTemporaryPassword(16);
            assertEquals(16, tempPass.length());
            assertTrue(tempPass.chars().anyMatch(Character::isUpperCase), "Must have uppercase");
            assertTrue(tempPass.chars().anyMatch(Character::isLowerCase), "Must have lowercase");
            assertTrue(tempPass.chars().anyMatch(Character::isDigit), "Must have digit");
            assertTrue(tempPass.chars().anyMatch(ch -> "!@#$%^&*".indexOf(ch) >= 0), "Must have special char");
        }
    }
}
