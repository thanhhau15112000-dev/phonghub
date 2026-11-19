package com.phonghub.adapter.out.supabase.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.application.port.out.PasswordRecoveryPort;
import com.phonghub.domain.exception.IdentityProviderUnavailableException;
import com.phonghub.domain.exception.InvalidPasswordRecoveryLinkException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** Gọi Supabase /auth/v1/recover và kiểm token khôi phục (#330). */
class SupabasePasswordRecoveryAdapterTest {
    private static final UUID USER = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final Instant EXP = Instant.parse("2026-11-12T04:00:00Z");

    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/v1/recover", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            apiKey.set(exchange.getRequestHeaders().getFirst("apikey"));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private static Jwt jwt(Object amr) {
        Jwt.Builder builder = Jwt.withTokenValue("tok").header("alg", "ES256")
            .subject(USER.toString()).claim("session_id", "sess-1").issuedAt(EXP.minusSeconds(3600)).expiresAt(EXP);
        if (amr != null) {
            builder.claim("amr", amr);
        }
        return builder.build();
    }

    private static SupabasePasswordRecoveryAdapter adapter(String url, JwtDecoder decoder) {
        return new SupabasePasswordRecoveryAdapter(null, url, "anon-key", decoder);
    }

    @Test
    @DisplayName("Gửi email: gọi recover bằng khóa anon, kèm redirect_to, không dùng khóa service role")
    void sendsRecoverRequest() throws IOException {
        String url = startServer(200);
        adapter(url, token -> jwt(null)).sendRecoveryEmail("tenant@mail.test", "https://app.example.com/reset-password");

        assertEquals("https://app.example.com/reset-password",
            URLDecoder.decode(query.get().replace("redirect_to=", ""), StandardCharsets.UTF_8));
        assertEquals("{\"email\":\"tenant@mail.test\"}", body.get());
        assertEquals("anon-key", apiKey.get());
        assertNull(authorization.get());
    }

    @Test
    @DisplayName("Không có redirect_to thì Supabase dùng Site URL")
    void omitsRedirectWhenNotConfigured() throws IOException {
        String url = startServer(200);
        adapter(url, token -> jwt(null)).sendRecoveryEmail("tenant@mail.test", null);
        assertNull(query.get());
    }

    @Test
    @DisplayName("Supabase lỗi (vd. vượt hạn mức email) thì báo dịch vụ không khả dụng")
    void providerErrorIsUnavailable() throws IOException {
        String url = startServer(429);
        assertThrows(IdentityProviderUnavailableException.class,
            () -> adapter(url, token -> jwt(null)).sendRecoveryEmail("tenant@mail.test", null));
    }

    @Test
    @DisplayName("Token khôi phục hợp lệ trả userId, mã phiên và hạn dùng")
    void acceptsRecoverySession() {
        PasswordRecoveryPort.RecoveryToken token = adapter("http://x", t -> jwt(List.of(Map.of("method", "recovery"))))
            .verifyRecoveryToken("tok");
        assertEquals(USER, token.userId());
        assertEquals("sess-1", token.sessionId());
        assertEquals(EXP, token.expiresAt());
    }

    @Test
    @DisplayName("Phiên đăng nhập bằng mật khẩu, thiếu amr, chữ ký sai hay hết hạn đều bị từ chối")
    void rejectsNonRecoverySessions() {
        assertThrows(InvalidPasswordRecoveryLinkException.class,
            () -> adapter("http://x", t -> jwt(List.of(Map.of("method", "password")))).verifyRecoveryToken("tok"));
        assertThrows(InvalidPasswordRecoveryLinkException.class,
            () -> adapter("http://x", t -> jwt(null)).verifyRecoveryToken("tok"));
        assertThrows(InvalidPasswordRecoveryLinkException.class,
            () -> adapter("http://x", t -> { throw new BadJwtException("expired"); }).verifyRecoveryToken("tok"));
    }

    @Test
    @DisplayName("Chỉ nhận phiên chứng minh sở hữu email")
    void emailProofMethods() {
        assertTrue(SupabasePasswordRecoveryAdapter.provesEmailOwnership(List.of(Map.of("method", "otp"))));
        assertTrue(SupabasePasswordRecoveryAdapter.provesEmailOwnership(List.of("magiclink")));
        assertFalse(SupabasePasswordRecoveryAdapter.provesEmailOwnership(List.of(Map.of("method", "token_refresh"))));
        assertFalse(SupabasePasswordRecoveryAdapter.provesEmailOwnership("recovery"));
    }
}
