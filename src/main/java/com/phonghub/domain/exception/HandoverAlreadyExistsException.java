package com.phonghub.domain.exception;

public class HandoverAlreadyExistsException extends DomainException {
    public HandoverAlreadyExistsException() {
        super("Hợp đồng này đã có biên bản check-in.");
    }
}
