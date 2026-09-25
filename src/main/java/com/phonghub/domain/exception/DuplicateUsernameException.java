package com.phonghub.domain.exception;

public class DuplicateUsernameException extends DomainException {
    public DuplicateUsernameException(String message) {
        super(message);
    }
}
