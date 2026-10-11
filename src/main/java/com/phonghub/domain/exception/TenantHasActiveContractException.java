package com.phonghub.domain.exception;

public class TenantHasActiveContractException extends DomainException {
    public TenantHasActiveContractException(String message) {
        super(message);
    }
}
