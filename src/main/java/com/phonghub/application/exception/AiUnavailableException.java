package com.phonghub.application.exception;

/** A safe, user-facing failure for disabled or unreachable AI providers. */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException() {
        super("Trợ lý AI hiện không khả dụng. Dữ liệu trong PhongHub vẫn xem được bình thường.");
    }
}
