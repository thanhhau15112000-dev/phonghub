package com.phonghub.domain.model;

import java.time.Instant;
import java.util.UUID;

public record StaffPropertyAssignment(
    UUID id,
    UUID staffUserId,
    UUID propertyId,
    boolean canCollectPayment,
    boolean canManageContracts,
    boolean canViewDocuments,
    boolean canManageRoomsAndAssets,
    Instant assignedAt
) {
    /** Tương thích với các caller cũ; quyền quản lý phòng mặc định bị tắt. */
    public StaffPropertyAssignment(UUID id, UUID staffUserId, UUID propertyId, boolean canCollectPayment,
                                   boolean canManageContracts, boolean canViewDocuments, Instant assignedAt) {
        this(id, staffUserId, propertyId, canCollectPayment, canManageContracts, canViewDocuments, false, assignedAt);
    }

    /** Tương thích: quyền xem giấy tờ đi theo quyền hợp đồng (dùng cho dữ liệu mẫu và code cũ). */
    public StaffPropertyAssignment(UUID id, UUID staffUserId, UUID propertyId, boolean canCollectPayment,
                                   boolean canManageContracts, Instant assignedAt) {
        this(id, staffUserId, propertyId, canCollectPayment, canManageContracts, canManageContracts, false, assignedAt);
    }

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
