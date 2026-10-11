package com.phonghub.domain.exception;

public class TenantProfileLockedException extends DomainException {
    public TenantProfileLockedException(String message) {
        super(message);
    }
}
