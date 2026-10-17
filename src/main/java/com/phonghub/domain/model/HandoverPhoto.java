package com.phonghub.domain.model;

import java.time.Instant;
import java.util.UUID;

public record HandoverPhoto(
    UUID id,
    UUID handoverId,
    HandoverPhotoKind kind,
    String imageKey,
    String contentType,
    Instant createdAt,
    UUID createdBy
) {
    public HandoverPhoto {
        if (id == null || handoverId == null || kind == null || imageKey == null || imageKey.isBlank()
            || contentType == null) {
            throw new IllegalArgumentException("Handover photo requires id, handover, kind, image and content type");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
