package com.phonghub.application.port.in;

import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AuthUseCase {

    /** Câu báo khi mật khẩu mới quá ngắn; dùng chung cho giao diện và API (#313). */
    String PASSWORD_TOO_SHORT = "Mật khẩu mới phải có ít nhất 8 ký tự.";
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

    /**
     * {@code initialPassword} trống thì hệ thống sinh mật khẩu tạm và luôn bắt đổi ở lần đăng nhập đầu; có giá trị thì
     * dùng mật khẩu admin đặt, {@code mustChangePassword} quyết định có bắt đổi hay không (#364).
     */
    record CreateUserCommand(
        String username,
        String email,
        String fullName,
        String phone,
        UserRole role,
        String identityCardNumber,
        String initialPassword,
        boolean mustChangePassword
    ) {
        public CreateUserCommand(String username, String email, String fullName, String phone, UserRole role,
                                 String identityCardNumber) {
            this(username, email, fullName, phone, role, identityCardNumber, null, true);
        }

        public CreateUserCommand(String username, String email, String fullName, String phone, UserRole role) {
            this(username, email, fullName, phone, role, null);
        }

        public boolean hasInitialPassword() {
            return initialPassword != null && !initialPassword.isEmpty();
        }

        // Không để mật khẩu lọt vào log khi record được in ra
        @Override
        public String toString() {
            return "CreateUserCommand[username=" + username + ", email=" + email + ", role=" + role
                + ", initialPassword=" + (hasInitialPassword() ? "***" : "none") + "]";
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
