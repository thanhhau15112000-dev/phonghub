package com.phonghub.adapter.out.identity;

import com.phonghub.application.port.out.IdentityProviderPort;
import java.util.UUID;

public class LocalDemoIdentityProviderAdapter implements IdentityProviderPort {

    @Override
    public RawTokenResponse login(String email, String password) {
        if (password == null || password.isBlank() || isInvalidDemoPassword(password)) {
            throw new com.phonghub.domain.exception.InvalidCredentialsException("Tên đăng nhập hoặc mật khẩu không đúng.");
        }
        String mockAccessToken = "demo-access-token-" + UUID.randomUUID();
        String mockRefreshToken = "demo-refresh-token-" + UUID.randomUUID();
        return new RawTokenResponse(mockAccessToken, mockRefreshToken, "Bearer", 3600);
    }

    private boolean isInvalidDemoPassword(String password) {
        String lower = password.toLowerCase();
        return lower.contains("wrong")
            || lower.contains("sai")
            || lower.contains("invalid")
            || lower.contains("fail")
            || lower.contains("error")
            || password.length() < 6;
    }

    @Override
    public RawTokenResponse refreshToken(String refreshToken) {
        String mockAccessToken = "demo-refreshed-token-" + UUID.randomUUID();
        return new RawTokenResponse(mockAccessToken, refreshToken, "Bearer", 3600);
    }

    @Override
    public void logout(String accessToken) {
        // No-op for local demo
    }

    @Override
    public void changePassword(UUID userId, String newPassword) {
        // No-op for local demo
    }

    @Override
    public void adminSetPassword(UUID userId, String temporaryPassword) {
        // No-op for local demo
    }

    @Override
    public UUID adminCreateUser(String email, String temporaryPassword) {
        return UUID.randomUUID();
    }

    @Override
    public void adminDeleteUser(UUID userId) {
        // No-op for local demo
    }
}
