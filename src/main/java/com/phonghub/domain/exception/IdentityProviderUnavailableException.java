package com.phonghub.domain.exception;

public class IdentityProviderUnavailableException extends DomainException {

    public IdentityProviderUnavailableException(String message) {
        super(message);
    }

    public IdentityProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
