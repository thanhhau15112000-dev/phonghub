package com.phonghub.domain.exception;

public class DocumentRevealDeniedException extends DomainException {
    public DocumentRevealDeniedException() {
        super("Mật khẩu xác nhận không đúng");
    }
}
