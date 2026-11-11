package com.phonghub.application.port.in;

/** Người dùng tự đặt lại mật khẩu khi quên (#330). */
public interface PasswordRecoveryUseCase {

    /**
     * Nhận yêu cầu quên mật khẩu theo tên đăng nhập hoặc email. Không báo tài khoản có tồn tại hay không: có email thì
     * gửi liên kết đặt lại; không có email hoặc không gửi được thì báo Quản trị viên cấp lại mật khẩu tạm.
     */
    void requestRecovery(String identifier);

    /** Đặt mật khẩu mới bằng token lấy từ liên kết trong email. */
    void resetPassword(String recoveryToken, String newPassword, String confirmPassword);

    /** Có gửi được email khôi phục không (đã cấu hình nhà cung cấp danh tính). */
    boolean isEmailRecoveryAvailable();
}
