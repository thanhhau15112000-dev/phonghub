package com.phonghub.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RoomAssetEvent(
    UUID id,
    UUID assetId,
    AssetEventType type,
    String description,
    BigDecimal cost,
    UUID maintenanceTicketId,
    Instant createdAt,
    UUID createdBy
) {
    public RoomAssetEvent {
        if (id == null || assetId == null || type == null) {
            throw new IllegalArgumentException("Event id, asset id and type are required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Mô tả sự cố không được để trống.");
        }
        if (cost == null) {
            cost = BigDecimal.ZERO;
        }
        if (cost.signum() < 0) {
            throw new IllegalArgumentException("Chi phí không được âm.");
        }
        description = description.trim();
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
