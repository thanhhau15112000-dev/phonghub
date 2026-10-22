package com.phonghub.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SepayGatewayOrder(
    String orderInvoiceNumber,
    UUID invoiceId,
    BigDecimal amount,
    Status status,
    String transactionId,
    Instant createdAt,
    Instant paidAt
) {
    public enum Status { PENDING, PAID, EXPIRED }

    public SepayGatewayOrder {
        if (orderInvoiceNumber == null || orderInvoiceNumber.isBlank() || invoiceId == null
            || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Valid order number, invoice id and positive amount are required");
        }
        if (status == null) {
            status = Status.PENDING;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
