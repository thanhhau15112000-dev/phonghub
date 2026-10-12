package com.phonghub.adapter.out.supabase.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.domain.exception.DuplicateEmailException;
import com.phonghub.domain.exception.IdentityProviderUnavailableException;
import com.phonghub.domain.exception.InvalidCredentialsException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class SupabaseIdentityProviderAdapterUnitTest {

    private static final String SUPABASE_URL = "https://test.supabase.co";
    private static final String ANON_KEY = "test-anon-key";
    private static final String SERVICE_ROLE_KEY = "test-service-key";

    private MockRestServiceServer mockServer;
    private SupabaseIdentityProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        adapter = new SupabaseIdentityProviderAdapter(
            restClient,
            SUPABASE_URL,
            ANON_KEY,
            SERVICE_ROLE_KEY,
            new ObjectMapper()
        );
    }

    // ==========================================
    // 1. LOGIN TESTS
    // ==========================================

    @Test
    @DisplayName("Login: 200 OK returns tokens successfully")
    void loginSuccessReturnsTokens() {
        mockServer.expect(requestTo(SUPABASE_URL + "/auth/v1/token?grant_type=password"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("apikey", ANON_KEY))
            .andRespond(withSuccess("""
                {
                    "access_token": "acc-token-123",
                    "refresh_token": "ref-token-456",
                    "token_type": "Bearer",
                    "expires_in": 3600
                }
                """, MediaType.APPLICATION_JSON));

        IdentityProviderPort.RawTokenResponse tokens = adapter.login("user@phonghub.local", "ValidPass123!");

        assertNotNull(tokens);
        assertEquals("acc-token-123", tokens.accessToken());
        assertEquals("ref-token-456", tokens.refreshToken());
        assertEquals("Bearer", tokens.tokenType());
        assertEquals(3600L, tokens.expiresIn());
        mockServer.verify();
    }

    @Test
    @DisplayName("Login: 400 Bad Request (invalid_grant) throws InvalidCredentialsException")
    void login400ThrowsInvalidCredentialsException() {
        mockServer.expect(requestTo(SUPABASE_URL + "/auth/v1/token?grant_type=password"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withBadRequest().body("""
                {"error": "invalid_grant", "error_description": "Invalid login credentials"}
                """).contentType(MediaType.APPLICATION_JSON));

        InvalidCredentialsException ex = assertThrows(
            InvalidCredentialsException.class,
            () -> adapter.login("user@phonghub.local", "WrongPassword")
        );
        assertTrue(ex.getMessage().contains("Invalid username or password"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Login: 429 Too Many Requests throws IdentityProviderUnavailableException with retry message")
    void login429ThrowsIdentityProviderUnavailableException() {
        mockServer.expect(requestTo(SUPABASE_URL + "/auth/v1/token?grant_type=password"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withRawStatus(429).body("""
                {"message": "Too many requests", "error_code": "over_request_rate_limit"}
                """).contentType(MediaType.APPLICATION_JSON));

        IdentityProviderUnavailableException ex = assertThrows(
            IdentityProviderUnavailableException.class,
            () -> adapter.login("user@phonghub.local", "password")
        );
        assertTrue(ex.getMessage().contains("thử lại sau"), "Message must mention retry: " + ex.getMessage());
        mockServer.verify();
    }

    @Test
    @DisplayName("Login: 401 Unauthorized (wrong anonKey) throws IdentityProviderUnavailableException + config error")
    void login401ThrowsIdentityProviderUnavailableException() {
        mockServer.expect(requestTo(SUPABASE_URL + "/auth/v1/token?grant_type=password"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("""
                {"message": "Invalid API key"}
                """).contentType(MediaType.APPLICATION_JSON));

        IdentityProviderUnavailableException ex = assertThrows(
            IdentityProviderUnavailableException.class,
            () -> adapter.login("user@phonghub.local", "password")
        );
        assertTrue(ex.getMessage().contains("cấu hình"), "Message must mention config issue: " + ex.getMessage());
        mockServer.verify();
    }

    @Test
    @DisplayName("Login: 403 Forbidden throws IdentityProviderUnavailableException + config error")
    void login403ThrowsIdentityProviderUnavailableException() {
        mockServer.expect(requestTo(SUPABASE_URL + "/auth/v1/token?grant_type=password"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.FORBIDDEN).body("""
                {"message": "Project paused or IP blocked"}
                """).contentType(MediaType.APPLICATION_JSON));

        IdentityProviderUnavailableException ex = assertThrows(
            IdentityProviderUnavailableException.class,
            () -> adapter.login("user@phonghub.local", "password")
        );
        assertTrue(ex.getMessage().contains("cấu hình"), "Message must mention config issue: " + ex.getMessage());
        mockServer.verify();
    }

    @Test
    @DisplayName("Login: 500 Server Error throws IdentityProviderUnavailableException")
    void login500ThrowsIdentityProviderUnavailableException() {
        mockServer.expect(requestTo(SUPABASE_URL + "/auth/v1/token?grant_type=password"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError());

        IdentityProviderUnavailableException ex = assertThrows(
            IdentityProviderUnavailableException.class,
            () -> adapter.login("user@phonghub.local", "password")
        );
        assertTrue(ex.getMessage().contains("unavailable") || ex.getMessage().contains("khả dụng"),
            "Message must mention service unavailable: " + ex.getMessage());
        mockServer.verify();
    }

    // ==========================================
    // 2. ADMIN CREATE USER TESTS
    // ==========================================

    @Test
    @DisplayName("adminCreateUser: 201 Created returns created UUID")
    void adminCreateUserSuccessReturnsUuid() {
        UUID createdId = UUID.randomUUID();
        mockServer.expect(requestTo(SUPABASE_URL + "/auth/v1/admin/users"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("apikey", SERVICE_ROLE_KEY))
            .andRespond(withStatus(HttpStatus.CREATED).body("""
                {
                    "id": "%s",
                    "email": "new@phonghub.local"
                }
                """.formatted(createdId)).contentType(MediaType.APPLICATION_JSON));

        UUID result = adapter.adminCreateUser("new@phonghub.local", "TempPass123!@#");

        assertEquals(createdId, result);
        mockServer.verify();
    }

    @Test
    @DisplayName("adminCreateUser: 422 with error_code=email_exists throws DuplicateEmailException")
    void adminCreateUserDuplicateEmailThrowsDuplicateEmailException() {
        mockServer.expect(requestTo(SUPABASE_URL + "/auth/v1/admin/users"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("""
                {
                    "error_code": "email_exists",
                    "msg": "A user with this email address has already been registered"
                }
                """).contentType(MediaType.APPLICATION_JSON));

        DuplicateEmailException ex = assertThrows(
            DuplicateEmailException.class,
            () -> adapter.adminCreateUser("existing@phonghub.local", "TempPass123!@#")
        );
        assertTrue(ex.getMessage().contains("existing@phonghub.local"));
        mockServer.verify();
    }

    @Test
    @DisplayName("adminCreateUser: 422 with msg containing 'already been registered' throws DuplicateEmailException")
    void adminCreateUserRegisteredMessageThrowsDuplicateEmailException() {
        mockServer.expect(requestTo(SUPABASE_URL + "/auth/v1/admin/users"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("""
                {
                    "msg": "User has already been registered"
                }
                """).contentType(MediaType.APPLICATION_JSON));

        DuplicateEmailException ex = assertThrows(
            DuplicateEmailException.class,
            () -> adapter.adminCreateUser("existing@phonghub.local", "TempPass123!@#")
        );
        assertTrue(ex.getMessage().contains("existing@phonghub.local"));
        mockServer.verify();
    }

    @Test
    @DisplayName("adminCreateUser: 422 with weak_password throws IllegalArgumentException")
    void adminCreateUserWeakPasswordThrowsIllegalArgumentException() {
        mockServer.expect(requestTo(SUPABASE_URL + "/auth/v1/admin/users"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("""
                {
                    "error_code": "weak_password",
                    "msg": "Password should be at least 8 characters"
                }
                """).contentType(MediaType.APPLICATION_JSON));

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> adapter.adminCreateUser("new@phonghub.local", "weak")
        );
        assertTrue(ex.getMessage().contains("Mật khẩu"), "Message should guide password strength: " + ex.getMessage());
        mockServer.verify();
    }

    @Test
    @DisplayName("adminCreateUser: 422 with unknown code throws IdentityProviderUnavailableException")
    void adminCreateUserUnknown422ThrowsUnavailableException() {
        mockServer.expect(requestTo(SUPABASE_URL + "/auth/v1/admin/users"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("""
                {
                    "error_code": "unexpected_error",
                    "msg": "Something else happened"
                }
                """).contentType(MediaType.APPLICATION_JSON));

        assertThrows(
            IdentityProviderUnavailableException.class,
            () -> adapter.adminCreateUser("new@phonghub.local", "TempPass123!@#")
        );
        mockServer.verify();
    }

    // ==========================================
    // 3. ADMIN SET PASSWORD TESTS
    // ==========================================

    @Test
    @DisplayName("adminSetPassword: 200 OK succeeds without exception")
    void adminSetPasswordSuccess() {
        UUID userId = UUID.randomUUID();
        mockServer.expect(requestTo(SUPABASE_URL + "/auth/v1/admin/users/" + userId))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header("apikey", SERVICE_ROLE_KEY))
            .andRespond(withSuccess());

        assertDoesNotThrow(() -> adapter.adminSetPassword(userId, "NewSecurePass123!@#"));
        mockServer.verify();
    }

    @Test
    @DisplayName("adminSetPassword: 422 weak_password throws IllegalArgumentException")
    void adminSetPasswordWeakPasswordThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        mockServer.expect(requestTo(SUPABASE_URL + "/auth/v1/admin/users/" + userId))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("""
                {
                    "error_code": "weak_password",
                    "msg": "Password should be at least 8 characters"
                }
                """).contentType(MediaType.APPLICATION_JSON));

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> adapter.adminSetPassword(userId, "1234")
        );
        assertTrue(ex.getMessage().contains("Mật khẩu"), "Message must explain password policy: " + ex.getMessage());
        mockServer.verify();
    }

    @Test
    @DisplayName("adminSetPassword: 422 validation error throws IllegalArgumentException (not service unavailable)")
    void adminSetPasswordValidationErrorThrowsIllegalArgumentException() {
        UUID userId = UUID.randomUUID();
        mockServer.expect(requestTo(SUPABASE_URL + "/auth/v1/admin/users/" + userId))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body("""
                {
                    "error_code": "same_password",
                    "msg": "New password cannot be the same as old password"
                }
                """).contentType(MediaType.APPLICATION_JSON));

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> adapter.adminSetPassword(userId, "OldPass123!")
        );
        assertTrue(ex.getMessage().contains("Mật khẩu không hợp lệ"), "Must be IllegalArgumentException: " + ex.getMessage());
        mockServer.verify();
    }

    @Test
    @DisplayName("adminSetPassword: 500 Server Error throws IdentityProviderUnavailableException")
    void adminSetPassword500ThrowsIdentityProviderUnavailableException() {
        UUID userId = UUID.randomUUID();
        mockServer.expect(requestTo(SUPABASE_URL + "/auth/v1/admin/users/" + userId))
            .andExpect(method(HttpMethod.PUT))
            .andRespond(withServerError());

        assertThrows(
            IdentityProviderUnavailableException.class,
            () -> adapter.adminSetPassword(userId, "NewPass123!@#")
        );
        mockServer.verify();
    }
}
