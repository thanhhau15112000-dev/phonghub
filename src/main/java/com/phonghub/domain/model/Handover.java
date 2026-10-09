package com.phonghub.domain.model;

import com.phonghub.domain.exception.InvalidHandoverStateException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Biên bản bàn giao check-in của một hợp đồng đang hiệu lực.
 * DRAFT -> PENDING_TENANT -> PENDING_APPROVAL -> APPROVED | REJECTED (REJECTED có thể mở lại về DRAFT).
 * Mọi chuyển trạng thái đi qua các phương thức dưới đây nên không thể bỏ qua bước.
 */
public record Handover(
    UUID id,
    UUID contractId,
    HandoverStatus status,
    BigDecimal initialElectricMeter,
    BigDecimal initialWaterMeter,
    String roomCondition,
    String keysProvided,
    String contractTermsHash,
    String houseRulesText,
    String houseRulesHash,
    String rejectionReason,
    Instant sentAt,
    boolean confirmedByTenant,
    Instant confirmedAt,
    UUID approvedBy,
    Instant approvedAt,
    Instant createdAt,
    UUID createdBy,
    Instant updatedAt,
    UUID updatedBy
) {
    public Handover {
        if (id == null || contractId == null) {
            throw new IllegalArgumentException("Handover id and contract id are required");
        }
        if (status == null) {
            status = HandoverStatus.DRAFT;
        }
        if (initialElectricMeter == null || initialElectricMeter.signum() < 0
            || initialWaterMeter == null || initialWaterMeter.signum() < 0) {
            throw new IllegalArgumentException("Initial electric and water meter readings must be zero or more");
        }
        if (roomCondition == null || roomCondition.isBlank()) {
            throw new IllegalArgumentException("Room condition cannot be blank");
        }
        roomCondition = roomCondition.trim();
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public static Handover draft(
        UUID contractId,
        BigDecimal electric,
        BigDecimal water,
        String roomCondition,
        String keysProvided,
        UUID actorId
    ) {
        Instant now = Instant.now();
        return new Handover(UUID.randomUUID(), contractId, HandoverStatus.DRAFT, electric, water, roomCondition,
            blankToNull(keysProvided), null, null, null, null, null, false, null, null, null, now, actorId, now,
            actorId);
    }

    public Handover edit(BigDecimal electric, BigDecimal water, String roomCondition, String keysProvided,
                         UUID actorId) {
        requireStatus(HandoverStatus.DRAFT, "chỉnh sửa");
        return new Handover(id, contractId, status, electric, water, roomCondition, blankToNull(keysProvided),
            contractTermsHash, houseRulesText, houseRulesHash, rejectionReason, sentAt, confirmedByTenant,
            confirmedAt, approvedBy, approvedAt, createdAt, createdBy, Instant.now(), actorId);
    }

    public Handover send(String termsHash, String rulesText, String rulesHash, UUID actorId) {
        requireStatus(HandoverStatus.DRAFT, "gửi cho khách");
        Instant now = Instant.now();
        return copy(HandoverStatus.PENDING_TENANT, termsHash, rulesText, rulesHash, null, now, false, null, null,
            null, now, actorId);
    }

    public Handover tenantConfirm(UUID actorId) {
        requireStatus(HandoverStatus.PENDING_TENANT, "xác nhận");
        Instant now = Instant.now();
        return copy(HandoverStatus.PENDING_APPROVAL, contractTermsHash, houseRulesText, houseRulesHash, null,
            sentAt, true, now, null, null, now, actorId);
    }

    public Handover approve(UUID actorId) {
        requireStatus(HandoverStatus.PENDING_APPROVAL, "duyệt");
        Instant now = Instant.now();
        return copy(HandoverStatus.APPROVED, contractTermsHash, houseRulesText, houseRulesHash, null, sentAt,
            confirmedByTenant, confirmedAt, actorId, now, now, actorId);
    }

    public Handover reject(String reason, UUID actorId) {
        requireStatus(HandoverStatus.PENDING_APPROVAL, "từ chối");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        return copy(HandoverStatus.REJECTED, contractTermsHash, houseRulesText, houseRulesHash, reason.trim(),
            sentAt, confirmedByTenant, confirmedAt, null, null, Instant.now(), actorId);
    }

    public Handover reopen(UUID actorId) {
        requireStatus(HandoverStatus.REJECTED, "mở lại");
        return copy(HandoverStatus.DRAFT, null, null, null, null, null, false, null, null, null, Instant.now(),
            actorId);
    }

    private Handover copy(
        HandoverStatus newStatus, String termsHash, String rulesText, String rulesHash, String reason,
        Instant newSentAt, boolean confirmed, Instant newConfirmedAt, UUID newApprovedBy, Instant newApprovedAt,
        Instant now, UUID actorId
    ) {
        return new Handover(id, contractId, newStatus, initialElectricMeter, initialWaterMeter, roomCondition,
            keysProvided, termsHash, rulesText, rulesHash, reason, newSentAt, confirmed, newConfirmedAt,
            newApprovedBy, newApprovedAt, createdAt, createdBy, now, actorId);
    }

    private void requireStatus(HandoverStatus expected, String action) {
        if (status != expected) {
            throw new InvalidHandoverStateException(String.format(
                "Không thể %s biên bản ở trạng thái %s (cần %s).", action, status, expected));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
