package com.phonghub.domain.exception;

/** Liên kết đặt lại mật khẩu sai, hết hạn, đã dùng, hoặc tài khoản không còn hoạt động (#330). */
public class InvalidPasswordRecoveryLinkException extends DomainException {
    public static final String MESSAGE =
        "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Hãy yêu cầu gửi lại email đặt lại mật khẩu.";

    public InvalidPasswordRecoveryLinkException() {
        super(MESSAGE);
    }
}
