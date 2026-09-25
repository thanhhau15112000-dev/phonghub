package com.phonghub.adapter.out.supabase.auth;

import com.phonghub.application.port.out.IdentityProviderPort;
import com.phonghub.domain.exception.IdentityProviderUnavailableException;
import com.phonghub.domain.exception.InvalidCredentialsException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class SupabaseIdentityProviderAdapter implements IdentityProviderPort {

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
                    throw new InvalidCredentialsException("Invalid username or password");
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                    throw new IdentityProviderUnavailableException("Supabase Auth service is currently unavailable (HTTP " + resp.getStatusCode() + ")");
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
            throw new IdentityProviderUnavailableException("Supabase Auth connection timeout or network failure: " + ex.getMessage(), ex);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw new InvalidCredentialsException("Invalid username or password");
            }
            throw new IdentityProviderUnavailableException("Supabase Auth provider outage (status " + ex.getStatusCode() + ")", ex);
        } catch (Exception ex) {
            throw new IdentityProviderUnavailableException("Supabase Auth communication failure: " + ex.getMessage(), ex);
        }
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
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new IdentityProviderUnavailableException("Failed to update password in Supabase Admin: HTTP " + resp.getStatusCode());
                })
                .toBodilessEntity();
        } catch (IdentityProviderUnavailableException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new IdentityProviderUnavailableException("Supabase Admin network timeout: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new IdentityProviderUnavailableException("Supabase Admin set password error: " + ex.getMessage(), ex);
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
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new IdentityProviderUnavailableException("Failed to create user in Supabase Admin: HTTP " + resp.getStatusCode());
                })
                .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String idStr = root.path("id").asText();
            return UUID.fromString(idStr);
        } catch (IdentityProviderUnavailableException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new IdentityProviderUnavailableException("Supabase Admin network timeout: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new IdentityProviderUnavailableException("Supabase Admin create user error: " + ex.getMessage(), ex);
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
}
