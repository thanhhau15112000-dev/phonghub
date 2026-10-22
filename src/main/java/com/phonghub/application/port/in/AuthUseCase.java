package com.phonghub.application.port.in;

import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public interface AuthUseCase {
    AuthTokenResponse login(String username, String password);
    AuthTokenResponse refreshToken(String refreshToken);
    void logout(String accessToken);
    void changePassword(String newPassword);
    PasswordResetResult adminResetPassword(UUID targetUserId);
    AdminCreateUserResult adminCreateUser(CreateUserCommand command);
    void adminDeleteUser(UUID targetUserId);

    record CreateUserCommand(
        String username,
        String email,
        String fullName,
        String phone,
        UserRole role
    ) {}

    // Serializable: được truyền qua flash attribute, lưu tạm trong session store
    record PasswordResetResult(
        UUID userId,
        String username,
        String temporaryPassword,
        boolean mustChangePassword,
        String message
    ) implements Serializable {}

    record AdminCreateUserResult(
        UUID id,
        String username,
        String email,
        String fullName,
        String phone,
        UserRole role,
        User.UserStatus status,
        boolean mustChangePassword,
        String temporaryPassword,
        Instant createdAt
    ) implements Serializable {}
}
