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
    UUID invoiceId,
    Instant createdAt,
    BigDecimal appliedAmount,
    BigDecimal excessAmount,
    PaymentReviewReason reviewReason,
    String gatewayTransactionId
) {
    public PaymentTransaction(
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
        UUID invoiceId,
        Instant createdAt
    ) {
        this(id, sepayId, gateway, transactionDate, accountNumber, subAccount, transferType, transferAmount,
            accumulated, code, content, referenceCode, description, status, contractId, invoiceId, createdAt,
            null, null, null, null);
    }

    public PaymentTransaction(
        UUID id, Long sepayId, String gateway, String transactionDate, String accountNumber, String subAccount,
        String transferType, BigDecimal transferAmount, BigDecimal accumulated, String code, String content,
        String referenceCode, String description, PaymentStatus status, UUID contractId, UUID invoiceId,
        Instant createdAt, BigDecimal appliedAmount, BigDecimal excessAmount, PaymentReviewReason reviewReason
    ) {
        this(id, sepayId, gateway, transactionDate, accountNumber, subAccount, transferType, transferAmount,
            accumulated, code, content, referenceCode, description, status, contractId, invoiceId, createdAt,
            appliedAmount, excessAmount, reviewReason, null);
    }

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
        if (appliedAmount == null) {
            appliedAmount = BigDecimal.ZERO;
        }
        if (excessAmount == null) {
            excessAmount = BigDecimal.ZERO;
        }
    }

    /** Cần người quản lý xem xét (trả dư, hóa đơn không còn nhận tiền hoặc không khớp hóa đơn nào). */
    public boolean needsReview() {
        return reviewReason != null;
    }
}
