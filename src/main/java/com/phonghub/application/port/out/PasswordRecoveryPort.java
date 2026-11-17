package com.phonghub.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Khôi phục mật khẩu qua email của nhà cung cấp danh tính (#330). Chỉ có khi chạy với Supabase; chế độ demo và
 * kiểm thử không có cổng này nên quên mật khẩu quay về cách cũ (báo Quản trị viên).
 */
public interface PasswordRecoveryPort {

    /**
     * Gửi email chứa liên kết đặt lại mật khẩu.
     *
     * @param redirectTo trang đặt lại mật khẩu của app; {@code null} thì nhà cung cấp dùng Site URL đã cấu hình
     * @throws com.phonghub.domain.exception.IdentityProviderUnavailableException khi không gửi được
     */
    void sendRecoveryEmail(String email, String redirectTo);

    /**
     * Kiểm token lấy từ liên kết trong email: chữ ký, hạn dùng, và phải là phiên được cấp qua email (khôi phục,
     * mã OTP hay magic link), không phải phiên đăng nhập bằng mật khẩu.
     *
     * @throws com.phonghub.domain.exception.InvalidPasswordRecoveryLinkException khi token không hợp lệ
     */
    RecoveryToken verifyRecoveryToken(String token);

    record RecoveryToken(UUID userId, String sessionId, Instant expiresAt) {
    }
}
