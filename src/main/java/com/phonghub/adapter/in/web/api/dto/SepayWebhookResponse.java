package com.phonghub.adapter.in.web.api.dto;

import java.util.UUID;

public record SepayWebhookResponse(
    boolean success,
    String message,
    UUID paymentId
) {
    public static SepayWebhookResponse ok(String message, UUID paymentId) {
        return new SepayWebhookResponse(true, message, paymentId);
    }

    public static SepayWebhookResponse error(String message) {
        return new SepayWebhookResponse(false, message, null);
    }
}
