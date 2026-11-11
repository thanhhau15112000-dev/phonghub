package com.phonghub.adapter.out.supabase.auth;

import com.phonghub.application.port.out.PasswordRecoveryPort;
import com.phonghub.domain.exception.IdentityProviderUnavailableException;
import com.phonghub.domain.exception.InvalidPasswordRecoveryLinkException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Quên mật khẩu qua Supabase Auth (#330): {@code POST /auth/v1/recover} gửi email chứa liên kết; liên kết đưa người
 * dùng về trang đặt lại của app kèm access token của phiên khôi phục. Token được kiểm bằng cùng bộ giải mã JWT của API.
 */
public class SupabasePasswordRecoveryAdapter implements PasswordRecoveryPort {
    /** Phiên được cấp sau khi người dùng chứng minh sở hữu email; không nhận phiên đăng nhập bằng mật khẩu. */
    static final Set<String> EMAIL_PROOF_METHODS = Set.of("recovery", "otp", "magiclink");

    private final RestClient restClient;
    private final String supabaseUrl;
    private final String anonKey;
    private final JwtDecoder jwtDecoder;

    public SupabasePasswordRecoveryAdapter(RestClient restClient, String supabaseUrl, String anonKey, JwtDecoder jwtDecoder) {
        this.restClient = restClient != null ? restClient : RestClient.create();
        this.supabaseUrl = supabaseUrl == null ? "" : supabaseUrl.replaceAll("/+$", "");
        this.anonKey = anonKey == null ? "" : anonKey;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public void sendRecoveryEmail(String email, String redirectTo) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(supabaseUrl + "/auth/v1/recover");
        if (redirectTo != null && !redirectTo.isBlank()) {
            uri.queryParam("redirect_to", redirectTo);
        }
        try {
            restClient.post()
                .uri(uri.encode().build().toUri())
                .contentType(MediaType.APPLICATION_JSON)
                .header("apikey", anonKey)
                .body(Map.of("email", email))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new IdentityProviderUnavailableException("Supabase Auth recover failed: " + ex.getClass().getSimpleName());
        }
    }

    @Override
    public RecoveryToken verifyRecoveryToken(String token) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException ex) {
            throw new InvalidPasswordRecoveryLinkException();
        }
        if (jwt == null || !provesEmailOwnership(jwt.getClaim("amr"))) {
            throw new InvalidPasswordRecoveryLinkException();
        }
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ex) {
            throw new InvalidPasswordRecoveryLinkException();
        }
        Object sessionId = jwt.getClaims().get("session_id");
        return new RecoveryToken(userId, sessionId == null ? null : sessionId.toString(), jwt.getExpiresAt());
    }

    static boolean provesEmailOwnership(Object amr) {
        if (!(amr instanceof List<?> entries)) {
            return false;
        }
        for (Object entry : entries) {
            Object method = entry instanceof Map<?, ?> map ? map.get("method") : entry;
            if (method != null && EMAIL_PROOF_METHODS.contains(method.toString())) {
                return true;
            }
        }
        return false;
    }
}
