package com.phonghub.domain.exception;

public class ChatMessageNotFoundException extends DomainException {
    public ChatMessageNotFoundException(String message) {
        super(message);
    }
}
