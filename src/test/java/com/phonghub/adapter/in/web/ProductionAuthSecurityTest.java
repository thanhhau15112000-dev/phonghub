package com.phonghub.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.phonghub.application.port.in.AuthTokenResponse;
import com.phonghub.application.port.in.AuthUseCase;
import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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

    @Test
    void healthEndpointIsPermittedWithoutAuthenticationInProd() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk());
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
}