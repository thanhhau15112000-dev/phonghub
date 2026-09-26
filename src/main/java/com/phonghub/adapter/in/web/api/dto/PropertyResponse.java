package com.phonghub.adapter.in.web.api.dto;

import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import java.time.Instant;
import java.util.UUID;

public record PropertyResponse(
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
    public static PropertyResponse from(Property property) {
        return new PropertyResponse(
            property.id(),
            property.name(),
            property.address(),
            property.description(),
            property.totalRooms(),
            property.ownerId(),
            property.approvalStatus(),
            property.rejectionReason(),
            property.createdAt()
        );
    }
}
