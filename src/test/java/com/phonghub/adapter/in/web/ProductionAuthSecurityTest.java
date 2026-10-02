package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.domain.exception.DuplicateEmailException;
import com.phonghub.domain.exception.DuplicateUsernameException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;

import com.phonghub.application.port.in.AuthTokenResponse;
import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.UserUseCase;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.phonghub.domain.exception.AccountDisabledException;
import com.phonghub.domain.exception.InvalidCredentialsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = {
    "supabase.url=https://mock.supabase.co",
    "supabase.anon-key=mock-anon-key",
    "supabase.service-role-key=mock-service-role-key",
    "supabase.jwks-uri=https://mock.supabase.co/auth/v1/.well-known/jwks.json",
    "supabase.jwt-issuer=https://mock.supabase.co/auth/v1",
    "supabase.jwt-audience=authenticated",
    "spring.datasource.url=jdbc:postgresql://localhost:5432/mock",
    "spring.datasource.username=mock",
    "spring.datasource.password=mock"
})
class ProductionAuthSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private javax.sql.DataSource dataSource;

    @MockitoBean
    private org.flywaydb.core.Flyway flyway;

    @MockitoBean
    private AuthUseCase authUseCase;

    @MockitoBean
    private UserRepositoryPort userRepository;

    @MockitoBean
    private PropertyUseCase propertyUseCase;

    @MockitoBean
    private UserUseCase userUseCase;

    @MockitoBean
    private com.phonghub.application.port.in.SepayWebhookUseCase sepayWebhookUseCase;

    @Test
    void healthEndpointIsPermittedWithoutAuthenticationInProd() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk());
    }

    @Test
    void browserUiRedirectsUnauthenticatedRequestsToLogin() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void browserLoginCreatesAnHttpSession() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authUseCase.login("admin", "secret123")).thenReturn(new AuthTokenResponse(
            "mock-access-token",
            "mock-refresh-token",
            "Bearer",
            3600L,
            new AuthTokenResponse.UserInfo(userId, "admin", "Admin User", UserRole.ADMIN, false)
        ));

        mockMvc.perform(post("/login")
                .with(csrf())
                .param("username", "admin")
                .param("password", "secret123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));
    }

    @Test
    void browserLoginRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/login")
                .param("username", "admin")
                .param("password", "secret123"))
            .andExpect(status().isForbidden());
    }

    @Test
    void browserLoginWithInvalidCredentialsDisplaysErrorMessageAndPreservesUsername() throws Exception {
        when(authUseCase.login("admin", "wrongpassword"))
            .thenThrow(new InvalidCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/login")
                .with(csrf())
                .param("username", "admin")
                .param("password", "wrongpassword"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("errorMessage", "Tên đăng nhập hoặc mật khẩu không đúng."))
            .andExpect(model().attribute("username", "admin"));
    }

    @Test
    void multipleFailedLoginAttemptsSuggestsForgotPassword() throws Exception {
        when(authUseCase.login("user1", "wrongpass"))
            .thenThrow(new InvalidCredentialsException("Invalid password"));

        MockHttpSession session = new MockHttpSession();

        // 1st attempt
        mockMvc.perform(post("/login")
                .session(session)
                .with(csrf())
                .param("username", "user1")
                .param("password", "wrongpass"))
            .andExpect(status().isOk())
            .andExpect(model().attributeDoesNotExist("suggestForgotPassword"));

        // 2nd attempt
        mockMvc.perform(post("/login")
                .session(session)
                .with(csrf())
                .param("username", "user1")
                .param("password", "wrongpass"))
            .andExpect(status().isOk())
            .andExpect(model().attributeDoesNotExist("suggestForgotPassword"));

        // 3rd attempt
        mockMvc.perform(post("/login")
                .session(session)
                .with(csrf())
                .param("username", "user1")
                .param("password", "wrongpass"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("suggestForgotPassword", true))
            .andExpect(model().attribute("failedAttempts", 3));

        // Visiting GET /login with this session also retains the suggestion
        mockMvc.perform(get("/login").session(session))
            .andExpect(status().isOk())
            .andExpect(model().attribute("suggestForgotPassword", true))
            .andExpect(model().attribute("failedAttempts", 3));
    }

    @Test
    void browserLoginWithDisabledAccountDisplaysSpecificErrorMessage() throws Exception {
        when(authUseCase.login("inactive_user", "secret123"))
            .thenThrow(new AccountDisabledException("Account is inactive"));

        mockMvc.perform(post("/login")
                .with(csrf())
                .param("username", "inactive_user")
                .param("password", "secret123"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("errorMessage", "Tài khoản hiện không hoạt động."))
            .andExpect(model().attribute("username", "inactive_user"));
    }

    @Test
    void browserLoginWithUnexpectedExceptionDisplaysSafeErrorMessage() throws Exception {
        when(authUseCase.login("admin", "secret123"))
            .thenThrow(new RuntimeException("Database timeout"));

        mockMvc.perform(post("/login")
                .with(csrf())
                .param("username", "admin")
                .param("password", "secret123"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("errorMessage", "Đã xảy ra lỗi trong quá trình đăng nhập. Vui lòng thử lại."))
            .andExpect(model().attribute("username", "admin"));
    }

    @Test
    void browserLoginPagePreservesUsernameQueryParam() throws Exception {
        mockMvc.perform(get("/login").param("username", "myusername").param("error", "true"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("errorMessage", "Tên đăng nhập hoặc mật khẩu không đúng."))
            .andExpect(model().attribute("username", "myusername"));
    }

    @Test
    void firstLoginBrowserSessionIsRedirectedToPasswordChange() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authUseCase.login("temporary", "secret123")).thenReturn(new AuthTokenResponse(
            "temporary-access-token",
            "temporary-refresh-token",
            "Bearer",
            3600L,
            new AuthTokenResponse.UserInfo(userId, "temporary", "Temporary User", UserRole.TENANT, true)
        ));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User(
            userId, "temporary", "temporary@phonghub.local", "Temporary User", "0900000001",
            UserRole.TENANT, User.UserStatus.ACTIVE, true, Instant.now()
        )));

        var loginResult = mockMvc.perform(post("/login")
                .with(csrf())
                .param("username", "temporary")
                .param("password", "secret123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/account/password"))
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        mockMvc.perform(get("/properties").session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/account/password"));
    }

    @Test
    void adminCanOpenAccountManagementPage() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(userUseCase.listUsers()).thenReturn(List.of(
            new User(
                UUID.randomUUID(), "admin", "admin@phonghub.local", "Admin User", null,
                UserRole.ADMIN, User.UserStatus.ACTIVE, false, Instant.now()
            ),
            new User(
                tenantId, "tenant1", "tenant@phonghub.local", "Tenant One", null,
                UserRole.TENANT, User.UserStatus.ACTIVE, true, Instant.now()
            )
        ));

        mockMvc.perform(get("/admin/users").session(loginAsAdmin()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("selectedRole", "ALL"))
            .andExpect(model().attribute("totalCount", 2))
            .andExpect(model().attribute("filteredCount", 2))
            .andExpect(model().attribute("currentPage", 1))
            .andExpect(model().attribute("totalPages", 1));
    }

    @Test
    void adminCanPaginateUsers() throws Exception {
        when(userUseCase.listUsers()).thenReturn(List.of(
            new User(UUID.randomUUID(), "u1", "u1@phonghub.local", "User 1", null, UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()),
            new User(UUID.randomUUID(), "u2", "u2@phonghub.local", "User 2", null, UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()),
            new User(UUID.randomUUID(), "u3", "u3@phonghub.local", "User 3", null, UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()),
            new User(UUID.randomUUID(), "u4", "u4@phonghub.local", "User 4", null, UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()),
            new User(UUID.randomUUID(), "u5", "u5@phonghub.local", "User 5", null, UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()),
            new User(UUID.randomUUID(), "u6", "u6@phonghub.local", "User 6", null, UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now())
        ));

        // Default size 5 with 6 items -> 2 pages, page 1 has 5 users
        mockMvc.perform(get("/admin/users").session(loginAsAdmin()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("currentPage", 1))
            .andExpect(model().attribute("totalPages", 2))
            .andExpect(model().attribute("filteredCount", 6));

        // Page 2 has the remaining 1 user
        mockMvc.perform(get("/admin/users").param("page", "2").session(loginAsAdmin()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("currentPage", 2))
            .andExpect(model().attribute("totalPages", 2));

        // Out-of-range page > totalPages redirects to valid page
        mockMvc.perform(get("/admin/users").param("page", "99").session(loginAsAdmin()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users?page=2"));

        // Out-of-range page < 1 redirects to clean base url
        mockMvc.perform(get("/admin/users").param("page", "0").session(loginAsAdmin()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"));
    }

    @Test
    void adminCanFilterUsersByRole() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(userUseCase.listUsers()).thenReturn(List.of(
            new User(
                UUID.randomUUID(), "admin", "admin@phonghub.local", "Admin User", null,
                UserRole.ADMIN, User.UserStatus.ACTIVE, false, Instant.now()
            ),
            new User(
                tenantId, "tenant1", "tenant@phonghub.local", "Tenant One", null,
                UserRole.TENANT, User.UserStatus.ACTIVE, true, Instant.now()
            )
        ));

        mockMvc.perform(get("/admin/users").param("role", "TENANT").session(loginAsAdmin()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("selectedRole", "TENANT"))
            .andExpect(model().attribute("totalCount", 2))
            .andExpect(model().attribute("filteredCount", 1));

        mockMvc.perform(get("/admin/users").param("role", "ADMIN").session(loginAsAdmin()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("selectedRole", "ADMIN"))
            .andExpect(model().attribute("totalCount", 2))
            .andExpect(model().attribute("filteredCount", 1));
    }

    @Test
    void adminCanSearchUsersByKeyword() throws Exception {
        when(userUseCase.listUsers()).thenReturn(List.of(
            new User(
                UUID.randomUUID(), "admin", "admin@phonghub.local", "Admin User", "0900000001",
                UserRole.ADMIN, User.UserStatus.ACTIVE, false, Instant.now()
            ),
            new User(
                UUID.randomUUID(), "staff1", "staff1@phonghub.local", "Staff One", "0900000002",
                UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()
            ),
            new User(
                UUID.randomUUID(), "tenant1", "tenant@phonghub.local", "Nguyen Van A", "0901234567",
                UserRole.TENANT, User.UserStatus.ACTIVE, true, Instant.now()
            )
        ));

        mockMvc.perform(get("/admin/users").param("q", "Nguyen").session(loginAsAdmin()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("totalCount", 1))
            .andExpect(model().attribute("filteredCount", 1))
            .andExpect(model().attribute("q", "Nguyen"));

        mockMvc.perform(get("/admin/users").param("q", "0900000002").session(loginAsAdmin()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("totalCount", 1))
            .andExpect(model().attribute("filteredCount", 1));

        // Search by email domain (matches all 3 mock users)
        mockMvc.perform(get("/admin/users").param("q", "phonghub.local").session(loginAsAdmin()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("totalCount", 3))
            .andExpect(model().attribute("filteredCount", 3));

        // Search by Vietnamese role name with accents
        mockMvc.perform(get("/admin/users").param("q", "Quản trị viên").session(loginAsAdmin()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("totalCount", 1))
            .andExpect(model().attribute("filteredCount", 1));

        // Search by unaccented role name
        mockMvc.perform(get("/admin/users").param("q", "quan tri vien").session(loginAsAdmin()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("totalCount", 1))
            .andExpect(model().attribute("filteredCount", 1));
    }

    @Test
    void adminCanCreateAccountFromAccountManagementPage() throws Exception {
        UUID createdId = UUID.randomUUID();
        when(authUseCase.adminCreateUser(any())).thenReturn(new AuthUseCase.AdminCreateUserResult(
            createdId,
            "tenant2",
            "tenant2@phonghub.local",
            "Tenant Two",
            null,
            UserRole.TENANT,
            User.UserStatus.ACTIVE,
            true,
            "TempPass#2026!",
            Instant.now()
        ));

        mockMvc.perform(post("/admin/users")
                .session(loginAsAdmin())
                .with(csrf())
                .param("username", "tenant2")
                .param("email", "tenant2@phonghub.local")
                .param("fullName", "Tenant Two")
                .param("role", "TENANT"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"));
    }

    @Test
    void adminCreateUserDuplicateEmailShowsFlashErrorMessageOnUi() throws Exception {
        when(authUseCase.adminCreateUser(any()))
            .thenThrow(new DuplicateEmailException("Email đã được sử dụng bởi một tài khoản khác: duplicate@phonghub.local"));

        mockMvc.perform(post("/admin/users")
                .session(loginAsAdmin())
                .with(csrf())
                .param("username", "tenant2")
                .param("email", "duplicate@phonghub.local")
                .param("fullName", "Tenant Two")
                .param("role", "TENANT"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attribute("errorMessage", "Email đã được sử dụng bởi một tài khoản khác: duplicate@phonghub.local"));
    }

    @Test
    void adminCreateUserDuplicateUsernameShowsFlashErrorMessageOnUi() throws Exception {
        when(authUseCase.adminCreateUser(any()))
            .thenThrow(new DuplicateUsernameException("Tên đăng nhập đã tồn tại trong hệ thống: duplicateuser"));

        mockMvc.perform(post("/admin/users")
                .session(loginAsAdmin())
                .with(csrf())
                .param("username", "duplicateuser")
                .param("email", "unique@phonghub.local")
                .param("fullName", "Tenant Two")
                .param("role", "TENANT"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attribute("errorMessage", "Tên đăng nhập đã tồn tại trong hệ thống: duplicateuser"));
    }

    @Test
    void adminCanResetTenantPasswordFromAccountManagementPage() throws Exception {
        UUID tenantId = UUID.randomUUID();
        User tenant = new User(
            tenantId, "tenant1", "tenant@phonghub.local", "Tenant One", null,
            UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now()
        );
        when(userUseCase.getUser(tenantId)).thenReturn(tenant);
        when(authUseCase.adminResetPassword(tenantId)).thenReturn(new AuthUseCase.PasswordResetResult(
            tenantId,
            "tenant1",
            "TempPass#2026!",
            true,
            "Temporary password generated. It will not be displayed again."
        ));

        mockMvc.perform(post("/admin/users/{userId}/reset-password", tenantId)
                .session(loginAsAdmin())
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"));
    }

    @Test
    void adminCanResetPasswordForStaffAndTechnician() throws Exception {
        UUID staffId = UUID.randomUUID();
        User staff = new User(
            staffId, "staff1", "staff1@phonghub.local", "Staff One", null,
            UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()
        );
        when(userUseCase.getUser(staffId)).thenReturn(staff);
        when(authUseCase.adminResetPassword(staffId)).thenReturn(new AuthUseCase.PasswordResetResult(
            staffId,
            "staff1",
            "TempPass#2026!",
            true,
            "Temporary password generated. It will not be displayed again."
        ));

        mockMvc.perform(post("/admin/users/{userId}/reset-password", staffId)
                .session(loginAsAdmin())
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"));
    }

    @Test
    void forgotPasswordPageIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/forgot-password"))
            .andExpect(status().isOk());
    }

    @Test
    void forgotPasswordWithValidUserReturnsInstruction() throws Exception {
        when(userRepository.findByUsername("staff1")).thenReturn(Optional.of(new User(
            UUID.randomUUID(), "staff1", "staff1@phonghub.local", "Staff One", "0900000002",
            UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()
        )));

        mockMvc.perform(post("/forgot-password")
                .with(csrf())
                .param("identifier", "staff1"))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("successMessage"));
    }

    @Test
    void forgotPasswordWithUnknownUserReturnsErrorMessage() throws Exception {
        when(userRepository.findByUsername("unknown_user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown_user")).thenReturn(Optional.empty());

        mockMvc.perform(post("/forgot-password")
                .with(csrf())
                .param("identifier", "unknown_user"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("errorMessage", "Không tìm thấy tài khoản tương ứng với thông tin đã nhập."))
            .andExpect(model().attribute("identifier", "unknown_user"));
    }

    @Test
    void unauthenticatedUserAccessingProfileRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/account/profile"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void authenticatedUserCanAccessProfilePage() throws Exception {
        MockHttpSession session = loginAsAdmin();
        when(userUseCase.getUser(any())).thenReturn(new User(
            UUID.randomUUID(),
            "admin",
            "admin@phonghub.local",
            "Admin User",
            "0901234567",
            UserRole.ADMIN,
            User.UserStatus.ACTIVE,
            false,
            Instant.now()
        ));

        mockMvc.perform(get("/account/profile").session(session))
            .andExpect(status().isOk())
            .andExpect(model().attributeExists("user"))
            .andExpect(model().attributeExists("currentUser"));
    }

    @Test
    void authenticatedUserCanUpdateProfile() throws Exception {
        MockHttpSession session = loginAsAdmin();
        when(userUseCase.updateProfile(any())).thenReturn(new User(
            UUID.randomUUID(),
            "admin",
            "admin@phonghub.local",
            "Admin New Name",
            "0987654321",
            UserRole.ADMIN,
            User.UserStatus.ACTIVE,
            false,
            Instant.now()
        ));

        mockMvc.perform(post("/account/profile")
                .session(session)
                .with(csrf())
                .param("fullName", "Admin New Name")
                .param("phone", "0987654321"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/account/profile"))
            .andExpect(flash().attribute("successMessage", "Cập nhật hồ sơ cá nhân thành công."));
    }

    @Test
    void updateProfileWithInvalidPhoneSetsErrorMessage() throws Exception {
        MockHttpSession session = loginAsAdmin();
        when(userUseCase.updateProfile(any()))
            .thenThrow(new IllegalArgumentException("Số điện thoại không hợp lệ (phải gồm 10 chữ số bắt đầu bằng 0)"));

        mockMvc.perform(post("/account/profile")
                .session(session)
                .with(csrf())
                .param("fullName", "Admin User")
                .param("phone", "123"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/account/profile"))
            .andExpect(flash().attribute("errorMessage", "Số điện thoại không hợp lệ (phải gồm 10 chữ số bắt đầu bằng 0)"));
    }

    @Test
    void protectedEndpointRequiresBearerTokenInProd() throws Exception {
        mockMvc.perform(get("/api/properties"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void demoActorHeaderIsIgnoredInProdProfileAndReturns401() throws Exception {
        mockMvc.perform(get("/api/properties")
                .header("X-Demo-User-Id", "00000000-0000-0000-0000-000000000001"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Invalid token signature returns 401 Unauthorized")
    void invalidBearerTokenReturns401() throws Exception {
        when(jwtDecoder.decode("invalid.jwt.token")).thenThrow(new BadJwtException("Invalid token signature"));

        mockMvc.perform(get("/api/properties")
                .header("Authorization", "Bearer invalid.jwt.token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT with wrong issuer returns 401 Unauthorized")
    void wrongIssuerReturns401() throws Exception {
        when(jwtDecoder.decode("wrong.issuer.jwt")).thenThrow(new BadJwtException("Invalid issuer"));

        mockMvc.perform(get("/api/properties")
                .header("Authorization", "Bearer wrong.issuer.jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT with wrong audience returns 401 Unauthorized")
    void wrongAudienceReturns401() throws Exception {
        when(jwtDecoder.decode("wrong.audience.jwt")).thenThrow(new BadJwtException("The required audience is missing or does not match"));

        mockMvc.perform(get("/api/properties")
                .header("Authorization", "Bearer wrong.audience.jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT with missing subject claim returns 401 Unauthorized")
    void missingSubjectReturns401() throws Exception {
        Jwt jwt = new Jwt(
            "missing.sub.jwt",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "HS256"),
            Map.of("aud", List.of("authenticated"), "iss", "https://mock.supabase.co/auth/v1")
        );
        when(jwtDecoder.decode("missing.sub.jwt")).thenReturn(jwt);

        mockMvc.perform(get("/api/properties")
                .header("Authorization", "Bearer missing.sub.jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("JWT for suspended user returns 401 Unauthorized")
    void jwtWithInactiveUserReturns401() throws Exception {
        UUID userId = UUID.randomUUID();
        User suspendedUser = new User(
            userId, "tenant1", "tenant1@phonghub.local", "Suspended Tenant", "0900000001",
            UserRole.TENANT, User.UserStatus.SUSPENDED, false, Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(suspendedUser));

        Jwt jwt = new Jwt(
            "suspended.jwt.token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "HS256"),
            Map.of("sub", userId.toString(), "aud", List.of("authenticated"), "iss", "https://mock.supabase.co/auth/v1")
        );
        when(jwtDecoder.decode("suspended.jwt.token")).thenReturn(jwt);

        mockMvc.perform(get("/api/properties")
                .header("Authorization", "Bearer suspended.jwt.token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Valid JWT allows access to business APIs when mustChangePassword is false")
    void validJwtSubjectMapsToDomainUserAndAllowsAccess() throws Exception {
        UUID userId = UUID.randomUUID();
        User activeUser = new User(
            userId, "admin", "admin@phonghub.local", "Admin User", "0900000000",
            UserRole.ADMIN, User.UserStatus.ACTIVE, false, Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        Jwt jwt = new Jwt(
            "valid.jwt.token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "HS256"),
            Map.of("sub", userId.toString(), "aud", List.of("authenticated"), "iss", "https://mock.supabase.co/auth/v1")
        );
        when(jwtDecoder.decode("valid.jwt.token")).thenReturn(jwt);

        mockMvc.perform(get("/api/properties")
                .header("Authorization", "Bearer valid.jwt.token"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Valid JWT with mustChangePassword=true is BLOCKED (403 Forbidden) from accessing business APIs")
    void validJwtWithMustChangePasswordTrueCannotAccessBusinessApis() throws Exception {
        UUID userId = UUID.randomUUID();
        User firstLoginUser = new User(
            userId, "tenant_temp", "temp@phonghub.local", "First Login Tenant", "0900000002",
            UserRole.TENANT, User.UserStatus.ACTIVE, true, Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(firstLoginUser));

        Jwt jwt = new Jwt(
            "temp.jwt.token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "HS256"),
            Map.of("sub", userId.toString(), "aud", List.of("authenticated"), "iss", "https://mock.supabase.co/auth/v1")
        );
        when(jwtDecoder.decode("temp.jwt.token")).thenReturn(jwt);

        mockMvc.perform(get("/api/properties")
                .header("Authorization", "Bearer temp.jwt.token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.title").value("Password Change Required"))
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Valid JWT with mustChangePassword=true is PERMITTED to access change-password endpoint")
    void validJwtWithMustChangePasswordTrueCanChangePassword() throws Exception {
        UUID userId = UUID.randomUUID();
        User firstLoginUser = new User(
            userId, "tenant_temp", "temp@phonghub.local", "First Login Tenant", "0900000002",
            UserRole.TENANT, User.UserStatus.ACTIVE, true, Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(firstLoginUser));

        Jwt jwt = new Jwt(
            "temp.jwt.token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "HS256"),
            Map.of("sub", userId.toString(), "aud", List.of("authenticated"), "iss", "https://mock.supabase.co/auth/v1")
        );
        when(jwtDecoder.decode("temp.jwt.token")).thenReturn(jwt);

        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer temp.jwt.token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"brandNewPassword123!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Password changed successfully"))
            .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    @DisplayName("Valid JWT with mustChangePassword=true is PERMITTED to call logout")
    void validJwtWithMustChangePasswordTrueCanLogout() throws Exception {
        UUID userId = UUID.randomUUID();
        User firstLoginUser = new User(
            userId, "tenant_temp", "temp@phonghub.local", "First Login Tenant", "0900000002",
            UserRole.TENANT, User.UserStatus.ACTIVE, true, Instant.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(firstLoginUser));

        Jwt jwt = new Jwt(
            "temp.jwt.token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "HS256"),
            Map.of("sub", userId.toString(), "aud", List.of("authenticated"), "iss", "https://mock.supabase.co/auth/v1")
        );
        when(jwtDecoder.decode("temp.jwt.token")).thenReturn(jwt);

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer temp.jwt.token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    @DisplayName("Public login endpoint is permitted without authentication")
    void loginEndpointIsPermittedWithoutAuthentication() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authUseCase.login("tenant1", "secret123")).thenReturn(new AuthTokenResponse(
            "mock-access-token",
            "mock-refresh-token",
            "Bearer",
            3600L,
            new AuthTokenResponse.UserInfo(userId, "tenant1", "Nguyen Van A", UserRole.TENANT, false)
        ));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"tenant1\",\"password\":\"secret123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
            .andExpect(jsonPath("$.user.username").value("tenant1"));
    }

    @Test
    @DisplayName("Refresh endpoint is public and only requires a refresh token")
    void refreshEndpointDoesNotRequireBearerToken() throws Exception {
        when(authUseCase.refreshToken("mock-refresh-token")).thenReturn(new AuthTokenResponse(
            "new-access-token",
            "new-refresh-token",
            "Bearer",
            3600L,
            null
        ));

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"mock-refresh-token\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("new-access-token"));
    }

    @Test
    @DisplayName("Admin create user returns dedicated AdminCreatedUserResponse with temporary password exactly once")
    void adminCreateUserReturnsTemporaryPasswordOnceInDedicatedResponse() throws Exception {
        UUID adminId = UUID.randomUUID();
        User adminUser = new User(
            adminId, "admin", "admin@phonghub.local", "Admin User", "0900000000",
            UserRole.ADMIN, User.UserStatus.ACTIVE, false, Instant.now()
        );
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        Jwt jwt = new Jwt(
            "admin.jwt.token",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of("alg", "HS256"),
            Map.of("sub", adminId.toString(), "aud", List.of("authenticated"), "iss", "https://mock.supabase.co/auth/v1")
        );
        when(jwtDecoder.decode("admin.jwt.token")).thenReturn(jwt);

        UUID newUserId = UUID.randomUUID();
        when(authUseCase.adminCreateUser(any())).thenReturn(new AuthUseCase.AdminCreateUserResult(
            newUserId,
            "newstaff",
            "staff@phonghub.local",
            "New Staff Member",
            "0909999888",
            UserRole.STAFF,
            User.UserStatus.ACTIVE,
            true,
            "TempPass#2026!",
            Instant.now()
        ));

        mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer admin.jwt.token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "newstaff",
                      "email": "staff@phonghub.local",
                      "fullName": "New Staff Member",
                      "phone": "0909999888",
                      "role": "STAFF"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(newUserId.toString()))
            .andExpect(jsonPath("$.username").value("newstaff"))
            .andExpect(jsonPath("$.temporaryPassword").value("TempPass#2026!"))
            .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    @DisplayName("Admin create user with duplicate email returns 409 Conflict")
    void createUserWithDuplicateEmailReturnsConflict409() throws Exception {
        UUID adminId = UUID.randomUUID();
        when(userRepository.findById(adminId)).thenReturn(Optional.of(new User(
            adminId, "admin", "admin@phonghub.local", "Admin User", "0900000000",
            UserRole.ADMIN, User.UserStatus.ACTIVE, false, Instant.now()
        )));
        when(jwtDecoder.decode("admin.jwt.token")).thenReturn(new Jwt(
            "admin.jwt.token", Instant.now(), Instant.now().plusSeconds(3600),
            Map.of("alg", "HS256"),
            Map.of("sub", adminId.toString(), "aud", List.of("authenticated"), "iss", "https://mock.supabase.co/auth/v1")
        ));
        when(authUseCase.adminCreateUser(any())).thenThrow(new DuplicateEmailException("Email already exists: duplicate@phonghub.local"));

        mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer admin.jwt.token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "newstaff",
                      "email": "duplicate@phonghub.local",
                      "fullName": "New Staff Member",
                      "phone": "0909999888",
                      "role": "STAFF"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Conflict"))
            .andExpect(jsonPath("$.detail").value("Email already exists: duplicate@phonghub.local"));
    }

    @Test
    @DisplayName("Admin create user with duplicate username returns 409 Conflict")
    void createUserWithDuplicateUsernameReturnsConflict409() throws Exception {
        UUID adminId = UUID.randomUUID();
        when(userRepository.findById(adminId)).thenReturn(Optional.of(new User(
            adminId, "admin", "admin@phonghub.local", "Admin User", "0900000000",
            UserRole.ADMIN, User.UserStatus.ACTIVE, false, Instant.now()
        )));
        when(jwtDecoder.decode("admin.jwt.token")).thenReturn(new Jwt(
            "admin.jwt.token", Instant.now(), Instant.now().plusSeconds(3600),
            Map.of("alg", "HS256"),
            Map.of("sub", adminId.toString(), "aud", List.of("authenticated"), "iss", "https://mock.supabase.co/auth/v1")
        ));
        when(authUseCase.adminCreateUser(any())).thenThrow(new DuplicateUsernameException("Username already exists: existinguser"));

        mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer admin.jwt.token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "existinguser",
                      "email": "unique@phonghub.local",
                      "fullName": "Existing Username Member",
                      "phone": "0909999888",
                      "role": "STAFF"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Conflict"))
            .andExpect(jsonPath("$.detail").value("Username already exists: existinguser"));
    }

    @Test
    @DisplayName("Admin create user with invalid email returns 400 Bad Request")
    void createUserWithInvalidEmailReturnsBadRequest400() throws Exception {
        UUID adminId = UUID.randomUUID();
        when(userRepository.findById(adminId)).thenReturn(Optional.of(new User(
            adminId, "admin", "admin@phonghub.local", "Admin User", "0900000000",
            UserRole.ADMIN, User.UserStatus.ACTIVE, false, Instant.now()
        )));
        when(jwtDecoder.decode("admin.jwt.token")).thenReturn(new Jwt(
            "admin.jwt.token", Instant.now(), Instant.now().plusSeconds(3600),
            Map.of("alg", "HS256"),
            Map.of("sub", adminId.toString(), "aud", List.of("authenticated"), "iss", "https://mock.supabase.co/auth/v1")
        ));

        mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer admin.jwt.token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "invalidemailuser",
                      "email": "not-an-email",
                      "fullName": "Invalid Email",
                      "phone": "0909999888",
                      "role": "STAFF"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Bad Request"))
            .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @Test
    void adminCanDeleteUserViaApi() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(jwtDecoder.decode(eq("admin.jwt.token"))).thenReturn(Jwt.withTokenValue("admin.jwt.token")
            .header("alg", "HS256")
            .subject(adminId.toString())
            .claim("aud", List.of("authenticated"))
            .claim("iss", "https://mock.supabase.co/auth/v1")
            .build());
        when(userRepository.findById(adminId)).thenReturn(Optional.of(new User(
            adminId, "admin", "admin@phonghub.local", "Admin User", "0900000000",
            UserRole.ADMIN, User.UserStatus.ACTIVE, false, Instant.now()
        )));

        mockMvc.perform(delete("/api/admin/users/{userId}", targetUserId)
                .header("Authorization", "Bearer admin.jwt.token"))
            .andExpect(status().isNoContent());

        verify(authUseCase).adminDeleteUser(targetUserId);
    }

    @Test
    void nonAdminCannotDeleteUserViaApiReturnsForbidden() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        when(jwtDecoder.decode(eq("staff.jwt.token"))).thenReturn(Jwt.withTokenValue("staff.jwt.token")
            .header("alg", "HS256")
            .subject(staffId.toString())
            .claim("aud", List.of("authenticated"))
            .claim("iss", "https://mock.supabase.co/auth/v1")
            .build());
        when(userRepository.findById(staffId)).thenReturn(Optional.of(new User(
            staffId, "staff", "staff@phonghub.local", "Staff User", "0900000002",
            UserRole.STAFF, User.UserStatus.ACTIVE, false, Instant.now()
        )));
        doThrow(new UnauthorizedPropertyAccessException("Action requires ADMIN role"))
            .when(authUseCase).adminDeleteUser(targetUserId);

        mockMvc.perform(delete("/api/admin/users/{userId}", targetUserId)
                .header("Authorization", "Bearer staff.jwt.token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminDeleteSelfOrAnotherAdminViaApiReturnsBadRequest() throws Exception {
        UUID targetAdminId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(jwtDecoder.decode(eq("admin.jwt.token"))).thenReturn(Jwt.withTokenValue("admin.jwt.token")
            .header("alg", "HS256")
            .subject(adminId.toString())
            .claim("aud", List.of("authenticated"))
            .claim("iss", "https://mock.supabase.co/auth/v1")
            .build());
        when(userRepository.findById(adminId)).thenReturn(Optional.of(new User(
            adminId, "admin", "admin@phonghub.local", "Admin User", "0900000000",
            UserRole.ADMIN, User.UserStatus.ACTIVE, false, Instant.now()
        )));
        doThrow(new IllegalArgumentException("Không được phép xoá tài khoản Quản trị viên (ADMIN)"))
            .when(authUseCase).adminDeleteUser(targetAdminId);

        mockMvc.perform(delete("/api/admin/users/{userId}", targetAdminId)
                .header("Authorization", "Bearer admin.jwt.token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid Argument"));
    }

    @Test
    void adminCanDeleteUserViaUiAndRedirectsWithSuccessMessage() throws Exception {
        UUID targetUserId = UUID.randomUUID();

        mockMvc.perform(post("/admin/users/{userId}/delete", targetUserId)
                .session(loginAsAdmin())
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attribute("successMessage", "Đã xoá tài khoản người dùng thành công."));

        verify(authUseCase).adminDeleteUser(targetUserId);
    }

    @Test
    void adminDeleteSelfViaUiSetsErrorMessage() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Quản trị viên không thể tự xoá tài khoản của chính mình"))
            .when(authUseCase).adminDeleteUser(targetUserId);

        mockMvc.perform(post("/admin/users/{userId}/delete", targetUserId)
                .session(loginAsAdmin())
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attribute("errorMessage", "Quản trị viên không thể tự xoá tài khoản của chính mình"));
    }

    @Test
    void deletedUserJwtReturns401OnApiCall() throws Exception {
        UUID deletedUserId = UUID.randomUUID();
        when(jwtDecoder.decode(eq("deleted.user.jwt"))).thenReturn(Jwt.withTokenValue("deleted.user.jwt")
            .header("alg", "HS256")
            .subject(deletedUserId.toString())
            .claim("aud", List.of("authenticated"))
            .claim("iss", "https://mock.supabase.co/auth/v1")
            .build());
        when(userRepository.findById(deletedUserId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/properties")
                .header("Authorization", "Bearer deleted.user.jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void deletedUserSessionIsImmediatelyInvalidatedAndRedirectedToLogin() throws Exception {
        MockHttpSession session = loginAsAdmin();
        // Simulate user deleted from repository
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/users").session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));

        assertTrue(session.isInvalid(), "Session must be invalidated immediately when user is deleted");
    }

    @Test
    void sepayWebhookEndpointIsPermittedWithoutJwtTokenInProd() throws Exception {
        when(sepayWebhookUseCase.processWebhook(any(), any()))
            .thenReturn(new com.phonghub.application.port.in.SepayWebhookUseCase.WebhookProcessResult(true, "OK", null));

        String payload = """
            {
              "id": 88001,
              "gateway": "MBBank",
              "accountNumber": "0389999999",
              "transferType": "in",
              "transferAmount": 1000000
            }
            """;
        mockMvc.perform(post("/api/v1/payments/sepay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        UUID adminId = UUID.randomUUID();
        when(authUseCase.login("admin", "secret123")).thenReturn(new AuthTokenResponse(
            "admin-access-token-" + adminId,
            "admin-refresh-token-" + adminId,
            "Bearer",
            3600L,
            new AuthTokenResponse.UserInfo(adminId, "admin", "Admin User", UserRole.ADMIN, false)
        ));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(new User(
            adminId, "admin", "admin@phonghub.local", "Admin User", "0900000000",
            UserRole.ADMIN, User.UserStatus.ACTIVE, false, Instant.now()
        )));

        var result = mockMvc.perform(post("/login")
                .with(csrf())
                .param("username", "admin")
                .param("password", "secret123"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
