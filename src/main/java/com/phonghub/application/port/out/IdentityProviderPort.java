package com.phonghub.application.port.out;

import java.util.UUID;

public interface IdentityProviderPort {
    RawTokenResponse login(String email, String password);
    RawTokenResponse refreshToken(String refreshToken);
    void logout(String accessToken);
    void changePassword(UUID userId, String newPassword);
    void adminSetPassword(UUID userId, String temporaryPassword);
    UUID adminCreateUser(String email, String temporaryPassword);
    void adminDeleteUser(UUID userId);

    record RawTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
    ) {}
}
