package com.phonghub.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Property(
    UUID id,
    String name,
    String address,
    String description,
    int totalRooms,
    UUID ownerId,
    PropertyApprovalStatus approvalStatus,
    String rejectionReason,
    Instant createdAt
) {
    public Property {
        if (id == null) {
            throw new IllegalArgumentException("Property id cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Property name cannot be blank");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Property address cannot be blank");
        }
        if (totalRooms < 0) {
            throw new IllegalArgumentException("Total rooms must be non-negative");
        }
        if (approvalStatus == null) {
            approvalStatus = PropertyApprovalStatus.APPROVED;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Property(
        UUID id,
        String name,
        String address,
        String description,
        int totalRooms,
        Instant createdAt
    ) {
        this(id, name, address, description, totalRooms, null, PropertyApprovalStatus.APPROVED, null, createdAt);
    }
}
