package com.phonghub.adapter.out.identity;

import com.phonghub.application.port.out.IdentityProviderPort;
import java.util.UUID;

public class LocalDemoIdentityProviderAdapter implements IdentityProviderPort {

    @Override
    public RawTokenResponse login(String email, String password) {
        String mockAccessToken = "demo-access-token-" + UUID.randomUUID();
        String mockRefreshToken = "demo-refresh-token-" + UUID.randomUUID();
        return new RawTokenResponse(mockAccessToken, mockRefreshToken, "Bearer", 3600);
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
