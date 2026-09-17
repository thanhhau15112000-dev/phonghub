package com.phonghub.application.port.in;

import com.phonghub.domain.model.UserRole;
import java.util.UUID;

public record AuthTokenResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UserInfo user
) {
    public record UserInfo(
        UUID id,
        String username,
        String fullName,
        UserRole role,
        boolean mustChangePassword
    ) {}
}
