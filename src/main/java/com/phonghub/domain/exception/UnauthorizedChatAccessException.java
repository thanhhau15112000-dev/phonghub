package com.phonghub.domain.exception;

public class UnauthorizedChatAccessException extends DomainException {
    public UnauthorizedChatAccessException(String message) {
        super(message);
    }
}
