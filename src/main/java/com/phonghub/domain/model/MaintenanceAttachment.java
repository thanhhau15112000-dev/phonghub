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
    Instant createdAt
) {
    public enum Kind { IMAGE, VIDEO }

    public MaintenanceAttachment {
        if (id == null || ticketId == null || kind == null || storageKey == null || storageKey.isBlank()
            || contentType == null || sizeBytes <= 0) {
            throw new IllegalArgumentException("Attachment requires id, ticket, kind, key, content type and size");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
