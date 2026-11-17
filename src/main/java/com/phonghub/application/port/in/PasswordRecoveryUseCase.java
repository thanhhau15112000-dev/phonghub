package com.phonghub.application.port.in;

/** Người dùng tự đặt lại mật khẩu khi quên (#330). */
public interface PasswordRecoveryUseCase {

    /**
     * Nhận yêu cầu quên mật khẩu theo tên đăng nhập hoặc email: có email thì gửi liên kết đặt lại; không có email hoặc
     * không gửi được thì báo Quản trị viên cấp lại mật khẩu tạm. Trả về việc đã làm để trang báo đúng cho người dùng (#341).
     */
    RecoveryResult requestRecovery(String identifier);

    /** Đặt mật khẩu mới bằng token lấy từ liên kết trong email. */
    void resetPassword(String recoveryToken, String newPassword, String confirmPassword);

    /** Có gửi được email khôi phục không (đã cấu hình nhà cung cấp danh tính). */
    boolean isEmailRecoveryAvailable();

    /** Kết quả một yêu cầu quên mật khẩu. */
    enum RecoveryResult {
        /** Không có tài khoản nào có tên đăng nhập hoặc email này. */
        NOT_FOUND,
        /** Đã gửi email có liên kết đặt lại mật khẩu. */
        EMAIL_SENT,
        /** Đã tạo thông báo để Quản trị viên cấp lại mật khẩu tạm. */
        ADMIN_NOTIFIED,
        /** Có tài khoản nhưng không gửi được gì (tài khoản Quản trị viên, không gửi được email). */
        NOT_SENT
    }
}
