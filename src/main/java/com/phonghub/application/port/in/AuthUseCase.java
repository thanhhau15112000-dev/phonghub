package com.phonghub.application.port.in;

import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AuthUseCase {
    AuthTokenResponse login(String username, String password);
    AuthTokenResponse refreshToken(String refreshToken);
    void logout(String accessToken);
    void changePassword(String newPassword);
    PasswordResetResult adminResetPassword(UUID targetUserId);
    AdminCreateUserResult adminCreateUser(CreateUserCommand command);

    /**
     * Kiểm tra hết các trường của form tạo tài khoản trong một lần, không ghi gì (#247).
     * Trả về tên trường (username, email, fullName, phone, role, identityCardNumber) -> câu lỗi; rỗng nếu hợp lệ.
     */
    Map<String, String> validateCreateUser(CreateUserCommand command);
    List<User> listTenantAccountsMissingProfiles();
    void adminAttachTenantProfile(UUID targetUserId, String identityCardNumber, String phone);
    void adminDeleteUser(UUID targetUserId);

    record CreateUserCommand(
        String username,
        String email,
        String fullName,
        String phone,
        UserRole role,
        String identityCardNumber
    ) {
        public CreateUserCommand(String username, String email, String fullName, String phone, UserRole role) {
            this(username, email, fullName, phone, role, null);
        }
    }

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
