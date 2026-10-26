package com.phonghub.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A durable payment applied to an invoice outside a bank gateway, such as offsetting rent against the deposit. */
public record InvoicePaymentRecord(
    UUID id,
    UUID contractId,
    UUID invoiceId,
    BigDecimal amount,
    Type type,
    Instant paidAt,
    UUID recordedBy,
    String note
) {
    public InvoicePaymentRecord {
        if (id == null || contractId == null || invoiceId == null || amount == null || amount.signum() <= 0
            || type == null || paidAt == null || recordedBy == null) {
            throw new IllegalArgumentException("A valid invoice payment record is required.");
        }
    }

    public enum Type {
        DEPOSIT_OFFSET
    }
}
