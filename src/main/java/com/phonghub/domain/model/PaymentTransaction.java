package com.phonghub.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentTransaction(
    UUID id,
    Long sepayId,
    String gateway,
    String transactionDate,
    String accountNumber,
    String subAccount,
    String transferType,
    BigDecimal transferAmount,
    BigDecimal accumulated,
    String code,
    String content,
    String referenceCode,
    String description,
    PaymentStatus status,
    UUID contractId,
    Instant createdAt
) {
    public PaymentTransaction {
        Objects.requireNonNull(id, "id cannot be null");
        if (transferType == null) {
            transferType = "in";
        }
        if (transferAmount == null) {
            transferAmount = BigDecimal.ZERO;
        }
        if (status == null) {
            status = PaymentStatus.SUCCESS;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
