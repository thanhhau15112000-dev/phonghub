package com.phonghub.domain.model;

import java.time.Instant;
import java.util.UUID;

public record PropertyHouseRules(UUID propertyId, String content, int version, Instant updatedAt, UUID updatedBy) {
    public PropertyHouseRules {
        if (propertyId == null) {
            throw new IllegalArgumentException("Property id is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("House rules cannot be blank");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("House rules version must be positive");
        }
        content = content.trim();
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }
}
