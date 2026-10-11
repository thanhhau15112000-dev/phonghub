package com.phonghub.domain.exception;

public class TooManyRevealAttemptsException extends DomainException {
    public TooManyRevealAttemptsException() {
        super("Nhập sai mật khẩu quá nhiều lần, vui lòng thử lại sau ít phút");
    }
}
