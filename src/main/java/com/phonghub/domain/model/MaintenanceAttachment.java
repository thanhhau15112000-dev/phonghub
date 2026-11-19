package com.phonghub.domain.model;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceAttachment(
    UUID id,
    UUID ticketId,
    Kind kind,
    String storageKey,
    String contentType,
    long sizeBytes,
    UUID uploadedBy,
    Instant createdAt,
    Phase phase,
    int round
) {
    public enum Kind { IMAGE, VIDEO }

    /** REPORT: tệp lúc báo hỏng. RESULT: tệp kết quả sửa chữa do kỹ thuật viên hoặc người quản lý tải. */
    public enum Phase { REPORT, RESULT }

    /** Tệp có loại nhưng chưa phân đợt (đợt 1). */
    public MaintenanceAttachment(
        UUID id, UUID ticketId, Kind kind, String storageKey, String contentType, long sizeBytes, UUID uploadedBy,
        Instant createdAt, Phase phase
    ) {
        this(id, ticketId, kind, storageKey, contentType, sizeBytes, uploadedBy, createdAt, phase, 1);
    }

    /** Tệp lúc báo hỏng (mặc định). */
    public MaintenanceAttachment(
        UUID id, UUID ticketId, Kind kind, String storageKey, String contentType, long sizeBytes, UUID uploadedBy,
        Instant createdAt
    ) {
        this(id, ticketId, kind, storageKey, contentType, sizeBytes, uploadedBy, createdAt, Phase.REPORT, 1);
    }

    public MaintenanceAttachment {
        if (id == null || ticketId == null || kind == null || storageKey == null || storageKey.isBlank()
            || contentType == null || sizeBytes <= 0) {
            throw new IllegalArgumentException("Attachment requires id, ticket, kind, key, content type and size");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (phase == null) {
            phase = Phase.REPORT;
        }
        if (round < 1) {
            round = 1;
        }
    }
}
