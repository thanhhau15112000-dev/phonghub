package com.phonghub.domain.model;

import com.phonghub.domain.exception.ConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Khoản thanh toán ngoài cổng SePay (tiền mặt, chuyển khoản không khớp mã) cần người có quyền thu tiền xác nhận (#205).
 * Người thuê gửi yêu cầu ({@link Source#TENANT_CLAIM}, chờ xác nhận) hoặc người thu tiền ghi nhận trực tiếp
 * ({@link Source#MANAGER_RECORD}, xác nhận ngay).
 */
public record PaymentClaim(
    UUID id,
    UUID invoiceId,
    UUID contractId,
    UUID propertyId,
    BigDecimal amount,
    Method method,
    String note,
    Source source,
    Status status,
    UUID createdBy,
    Instant createdAt,
    UUID decidedBy,
    Instant decidedAt,
    String rejectReason
) {

    public enum Method { BANK_TRANSFER, CASH }

    public enum Source { TENANT_CLAIM, MANAGER_RECORD }

    public enum Status { PENDING, CONFIRMED, REJECTED }

    public PaymentClaim {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(invoiceId, "invoiceId");
        Objects.requireNonNull(contractId, "contractId");
        Objects.requireNonNull(propertyId, "propertyId");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Số tiền thanh toán phải lớn hơn 0.");
        }
        if (note != null && note.length() > 1000) {
            throw new IllegalArgumentException("Ghi chú tối đa 1000 ký tự.");
        }
    }

    /** Người thuê báo đã thanh toán, chờ người thu tiền xác nhận. */
    public static PaymentClaim submitted(Invoice invoice, UUID propertyId, BigDecimal amount, Method method, String note,
                                         UUID tenantUserId) {
        return new PaymentClaim(UUID.randomUUID(), invoice.getId(), invoice.getContractId(), propertyId, amount, method,
            clean(note), Source.TENANT_CLAIM, Status.PENDING, tenantUserId, Instant.now(), null, null, null);
    }

    /** Người thu tiền ghi nhận trực tiếp: xác nhận ngay khi tạo. */
    public static PaymentClaim recorded(Invoice invoice, UUID propertyId, BigDecimal amount, Method method, String note,
                                        UUID collectorId) {
        Instant now = Instant.now();
        return new PaymentClaim(UUID.randomUUID(), invoice.getId(), invoice.getContractId(), propertyId, amount, method,
            clean(note), Source.MANAGER_RECORD, Status.CONFIRMED, collectorId, now, collectorId, now, null);
    }

    public PaymentClaim confirmed(UUID collectorId) {
        assertPending();
        return new PaymentClaim(id, invoiceId, contractId, propertyId, amount, method, note, source, Status.CONFIRMED,
            createdBy, createdAt, collectorId, Instant.now(), null);
    }

    public PaymentClaim rejected(UUID collectorId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cần nhập lý do từ chối.");
        }
        if (reason.trim().length() > 500) {
            throw new IllegalArgumentException("Lý do tối đa 500 ký tự.");
        }
        assertPending();
        return new PaymentClaim(id, invoiceId, contractId, propertyId, amount, method, note, source, Status.REJECTED,
            createdBy, createdAt, collectorId, Instant.now(), reason.trim());
    }

    private void assertPending() {
        if (status != Status.PENDING) {
            throw new ConflictException("Yêu cầu thanh toán đã được xử lý (" + status + ").");
        }
    }

    private static String clean(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }
}
