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
            throw new IllegalArgumentException("Mã tài sản không được để trống.");
        }
        if (assetCode.trim().length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("Mã tài sản tối đa " + MAX_CODE_LENGTH + " ký tự.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tên tài sản không được để trống.");
        }
        if (name.trim().length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Tên tài sản tối đa " + MAX_NAME_LENGTH + " ký tự.");
        }
        if (serialNumber != null && serialNumber.length() > MAX_SERIAL_LENGTH) {
            throw new IllegalArgumentException("Số serial tối đa " + MAX_SERIAL_LENGTH + " ký tự.");
        }
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("Ghi chú tình trạng tối đa " + MAX_NOTE_LENGTH + " ký tự.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Số lượng tài sản phải lớn hơn 0.");
        }
        if (status == null) {
            throw new IllegalArgumentException("Asset status is required");
        }
        if (estimatedValue == null) {
            estimatedValue = BigDecimal.ZERO;
        }
        if (estimatedValue.signum() < 0) {
            throw new IllegalArgumentException("Giá trị ước tính không được âm.");
        }
        if (estimatedValue.compareTo(MAX_ESTIMATED_VALUE) > 0) {
            throw new IllegalArgumentException("Giá trị ước tính quá lớn (tối đa 9.999.999.999 đ).");
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

    // Giới hạn theo cột của bảng room_assets; vượt quá thì DB báo lỗi và trang trả 500 thay vì báo lỗi nhập liệu.
    public static final int MAX_CODE_LENGTH = 40;
    public static final int MAX_NAME_LENGTH = 150;
    public static final int MAX_SERIAL_LENGTH = 100;
    public static final int MAX_NOTE_LENGTH = 255;
    public static final BigDecimal MAX_ESTIMATED_VALUE = new BigDecimal("9999999999.99");
}
