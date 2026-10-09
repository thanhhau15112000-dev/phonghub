package com.phonghub.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RoomAsset(
    UUID id,
    UUID roomId,
    String assetCode,
    String name,
    String serialNumber,
    int quantity,
    AssetStatus status,
    String note,
    String imageUrl,
    BigDecimal estimatedValue,
    Instant deletedAt,
    Instant createdAt,
    UUID createdBy,
    Instant updatedAt,
    UUID updatedBy
) {
    public RoomAsset {
        if (id == null || roomId == null) {
            throw new IllegalArgumentException("Asset id and room id are required");
        }
        if (assetCode == null || assetCode.isBlank()) {
            throw new IllegalArgumentException("Asset code cannot be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Asset name cannot be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Asset quantity must be positive");
        }
        if (status == null) {
            throw new IllegalArgumentException("Asset status is required");
        }
        if (estimatedValue == null) {
            estimatedValue = BigDecimal.ZERO;
        }
        if (estimatedValue.signum() < 0) {
            throw new IllegalArgumentException("Estimated value cannot be negative");
        }
        assetCode = assetCode.trim();
        name = name.trim();
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public RoomAsset update(
        String name,
        String serialNumber,
        int quantity,
        String note,
        String imageUrl,
        BigDecimal estimatedValue,
        UUID actorId
    ) {
        return new RoomAsset(id, roomId, assetCode, name, serialNumber, quantity, status, note, imageUrl,
            estimatedValue, deletedAt, createdAt, createdBy, Instant.now(), actorId);
    }

    public RoomAsset withStatus(AssetStatus newStatus, UUID actorId) {
        return new RoomAsset(id, roomId, assetCode, name, serialNumber, quantity, newStatus, note, imageUrl,
            estimatedValue, deletedAt, createdAt, createdBy, Instant.now(), actorId);
    }

    public RoomAsset softDelete(UUID actorId) {
        Instant now = Instant.now();
        return new RoomAsset(id, roomId, assetCode, name, serialNumber, quantity, status, note, imageUrl,
            estimatedValue, now, createdAt, createdBy, now, actorId);
    }
}
