package com.phonghub.domain.model;

import java.time.Instant;
import java.util.UUID;

public record StaffPropertyAssignment(
    UUID id,
    UUID staffUserId,
    UUID propertyId,
    boolean canCollectPayment,
    boolean canManageContracts,
    Instant assignedAt
) {
    public StaffPropertyAssignment {
        if (id == null) {
            throw new IllegalArgumentException("Assignment id cannot be null");
        }
        if (staffUserId == null) {
            throw new IllegalArgumentException("Staff user id cannot be null");
        }
        if (propertyId == null) {
            throw new IllegalArgumentException("Property id cannot be null");
        }
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
    }
}
