package com.phonghub.domain.exception;

public class UnauthorizedWebhookException extends DomainException {
    public UnauthorizedWebhookException(String message) {
        super(message);
    }
}
