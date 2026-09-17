package com.phonghub.domain.exception;

public class PasswordChangeRequiredException extends DomainException {
    public PasswordChangeRequiredException(String message) {
        super(message);
    }
}
