package com.phonghub.adapter.out.supabase.auth;

import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.domain.exception.DuplicateEmailException;
import com.phonghub.domain.exception.IdentityProviderUnavailableException;
import com.phonghub.domain.exception.InvalidCredentialsException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class SupabaseIdentityProviderAdapter implements IdentityProviderPort {

    private static final Logger log = LoggerFactory.getLogger(SupabaseIdentityProviderAdapter.class);

    private final RestClient restClient;
    private final String supabaseUrl;
    private final String anonKey;
    private final String serviceRoleKey;
    private final ObjectMapper objectMapper;

    public SupabaseIdentityProviderAdapter(
        RestClient restClient,
        String supabaseUrl,
        String anonKey,
        String serviceRoleKey,
        ObjectMapper objectMapper
    ) {
        this.restClient = restClient != null ? restClient : RestClient.create();
        this.supabaseUrl = supabaseUrl != null ? supabaseUrl.replaceAll("/+$", "") : "";
        this.anonKey = anonKey != null ? anonKey : "";
        this.serviceRoleKey = serviceRoleKey != null ? serviceRoleKey : "";
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public RawTokenResponse login(String email, String password) {
        String endpoint = supabaseUrl + "/auth/v1/token?grant_type=password";
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", password
            ));

            String responseBody = restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("apikey", anonKey)
                .body(payload)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    int status = resp.getStatusCode().value();
                    byte[] bodyBytes = resp.getBody().readAllBytes();
                    String body = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
                    mapLoginClientError(status, body);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    throw new IdentityProviderUnavailableException(
                        "Supabase Auth service is currently unavailable (HTTP " + resp.getStatusCode() + ")");
                })
                .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String accessToken = root.path("access_token").asText();
            String refreshToken = root.path("refresh_token").asText();
            String tokenType = root.path("token_type").asText("Bearer");
            long expiresIn = root.path("expires_in").asLong(3600);

            return new RawTokenResponse(accessToken, refreshToken, tokenType, expiresIn);
        } catch (InvalidCredentialsException | IdentityProviderUnavailableException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new IdentityProviderUnavailableException(
                "Supabase Auth connection timeout or network failure: " + ex.getMessage(), ex);
        } catch (RestClientResponseException ex) {
            mapLoginClientError(ex.getStatusCode().value(), ex.getResponseBodyAsString());
            // mapLoginClientError always throws; unreachable but required by compiler
            throw new IdentityProviderUnavailableException(
                "Supabase Auth provider outage (status " + ex.getStatusCode() + ")", ex);
        } catch (Exception ex) {
            throw new IdentityProviderUnavailableException(
                "Supabase Auth communication failure: " + ex.getMessage(), ex);
        }
    }

    /**
     * Maps a 4xx HTTP status + body from Supabase /auth/v1/token to the correct domain exception.
     * <ul>
     *   <li>429 → {@link IdentityProviderUnavailableException} (rate limit)</li>
     *   <li>401/403 → {@link IdentityProviderUnavailableException} + ERROR log (apikey/config issue)</li>
     *   <li>400 invalid credentials → {@link InvalidCredentialsException}</li>
     *   <li>Other 4xx → {@link IdentityProviderUnavailableException}</li>
     * </ul>
     * This method always throws; it never returns normally.
     */
    private void mapLoginClientError(int status, String body) {
        if (status == 429) {
            throw new IdentityProviderUnavailableException(
                "Quá nhiều yêu cầu đăng nhập. Vui lòng thử lại sau.");
        }
        if (status == 401 || status == 403) {
            // apikey sai hoặc project bị khoá — lỗi cấu hình, không phải do credentials người dùng
            log.error("Supabase Auth apikey/project configuration error: HTTP {}", status);
            throw new IdentityProviderUnavailableException(
                "Dịch vụ xác thực đang gặp sự cố cấu hình (HTTP " + status + ").");
        }
        String errorCode = extractErrorCode(body);
        if (status == 400 && ("invalid_grant".equals(errorCode)
                || "invalid_credentials".equals(errorCode)
                || body.toLowerCase(java.util.Locale.ROOT).contains("invalid login credentials"))) {
            throw new InvalidCredentialsException("Invalid username or password");
        }
        log.error("Unexpected Supabase Auth login client error: HTTP {}, error_code={}", status, errorCode);
        throw new IdentityProviderUnavailableException(
            "Supabase Auth rejected the login request unexpectedly (HTTP " + status + ").");
    }

    @Override
    public RawTokenResponse refreshToken(String refreshToken) {
        String endpoint = supabaseUrl + "/auth/v1/token?grant_type=refresh_token";
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "refresh_token", refreshToken
            ));

            String responseBody = restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("apikey", anonKey)
                .body(payload)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw new InvalidCredentialsException("Invalid or expired refresh token");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    throw new IdentityProviderUnavailableException("Supabase Auth service unavailable (HTTP " + resp.getStatusCode() + ")");
                })
                .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String accessToken = root.path("access_token").asText();
            String newRefreshToken = root.path("refresh_token").asText(refreshToken);
            String tokenType = root.path("token_type").asText("Bearer");
            long expiresIn = root.path("expires_in").asLong(3600);

            return new RawTokenResponse(accessToken, newRefreshToken, tokenType, expiresIn);
        } catch (InvalidCredentialsException | IdentityProviderUnavailableException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new IdentityProviderUnavailableException("Supabase Auth timeout or network failure: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new IdentityProviderUnavailableException("Failed to refresh token: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        String endpoint = supabaseUrl + "/auth/v1/logout";
        try {
            restClient.post()
                .uri(endpoint)
                .header("apikey", anonKey)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception ignored) {
            // Logout on Supabase is best effort
        }
    }

    @Override
    public void changePassword(UUID userId, String newPassword) {
        adminSetPassword(userId, newPassword);
    }

    @Override
    public void adminSetPassword(UUID userId, String temporaryPassword) {
        String endpoint = supabaseUrl + "/auth/v1/admin/users/" + userId;
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "password", temporaryPassword
            ));

            restClient.put()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("apikey", serviceRoleKey)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                .body(payload)
                .retrieve()
                .onStatus(code -> code.value() == 422, (req, resp) -> {
                    byte[] bodyBytes = resp.getBody().readAllBytes();
                    String body = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
                    String errorCode = extractErrorCode(body);
                    if ("weak_password".equals(errorCode)) {
                        throw new IllegalArgumentException(
                            "Mật khẩu không đủ độ mạnh theo yêu cầu của Supabase (weak_password). "
                            + "Mật khẩu phải có ít nhất 8 ký tự, gồm chữ hoa, chữ thường, chữ số và ký tự đặc biệt.");
                    }
                    throw new IllegalArgumentException(
                        "Mật khẩu không hợp lệ theo yêu cầu của hệ thống xác thực (422). Error code: " + errorCode);
                })
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new IdentityProviderUnavailableException(
                        "Failed to update password in Supabase Admin: HTTP " + resp.getStatusCode());
                })
                .toBodilessEntity();
        } catch (IdentityProviderUnavailableException | IllegalArgumentException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new IdentityProviderUnavailableException(
                "Supabase Admin network timeout: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new IdentityProviderUnavailableException(
                "Supabase Admin set password error: " + ex.getMessage(), ex);
        }
    }

    @Override
    public UUID adminCreateUser(String email, String temporaryPassword) {
        String endpoint = supabaseUrl + "/auth/v1/admin/users";
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", temporaryPassword,
                "email_confirm", true
            ));

            String responseBody = restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("apikey", serviceRoleKey)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                .body(payload)
                .retrieve()
                .onStatus(code -> code.value() == 422, (req, resp) -> {
                    byte[] bodyBytes = resp.getBody().readAllBytes();
                    String body = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
                    String errorCode = extractErrorCode(body);
                    if ("email_exists".equals(errorCode) || body.contains("already been registered")) {
                        throw new DuplicateEmailException(
                            "Email đã tồn tại trong hệ thống xác thực: " + email);
                    }
                    if ("weak_password".equals(errorCode)) {
                        throw new IllegalArgumentException(
                            "Mật khẩu tạm thời không đủ độ mạnh theo yêu cầu của Supabase (weak_password). "
                            + "Mật khẩu phải có ít nhất 8 ký tự, gồm chữ hoa, chữ thường, chữ số và ký tự đặc biệt.");
                    }
                    throw new IdentityProviderUnavailableException(
                        "Supabase validation error when creating user (422). Error code: " + errorCode);
                })
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new IdentityProviderUnavailableException(
                        "Failed to create user in Supabase Admin: HTTP " + resp.getStatusCode());
                })
                .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String idStr = root.path("id").asText();
            return UUID.fromString(idStr);
        } catch (DuplicateEmailException | IllegalArgumentException | IdentityProviderUnavailableException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new IdentityProviderUnavailableException(
                "Supabase Admin network timeout: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new IdentityProviderUnavailableException(
                "Supabase Admin create user error: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void adminDeleteUser(UUID userId) {
        String endpoint = supabaseUrl + "/auth/v1/admin/users/" + userId;
        try {
            restClient.delete()
                .uri(endpoint)
                .header("apikey", serviceRoleKey)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new IdentityProviderUnavailableException("Failed to delete user in Supabase Admin: HTTP " + resp.getStatusCode());
                })
                .toBodilessEntity();
        } catch (IdentityProviderUnavailableException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new IdentityProviderUnavailableException("Supabase Admin network timeout: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new IdentityProviderUnavailableException("Supabase Admin delete user error: " + ex.getMessage(), ex);
        }
    }

    /**
     * Trích xuất error_code từ body JSON của Supabase
     * (ví dụ: {@code "email_exists"}, {@code "weak_password"}).
     * Trả về chuỗi rỗng nếu không parse được.
     */
    private String extractErrorCode(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            // Supabase trả về { "error_code": "...", "msg": "..." }
            String code = root.path("error_code").asText("");
            if (!code.isBlank()) return code;
            // Fallback: trường "code" hoặc "error"
            code = root.path("code").asText("");
            if (!code.isBlank()) return code;
            return root.path("error").asText("");
        } catch (Exception ignored) {
            return "";
        }
    }
}
