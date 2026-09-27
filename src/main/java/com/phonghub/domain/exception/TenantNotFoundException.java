package com.phonghub.domain.exception;

public class TenantNotFoundException extends DomainException {

    public TenantNotFoundException(String message) {
        super(message);
    }
}
