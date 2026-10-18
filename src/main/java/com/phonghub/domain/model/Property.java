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
        validateInfo(name, address, totalRooms);
        if (approvalStatus == null) {
            approvalStatus = PropertyApprovalStatus.VERIFIED;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /** Tên và địa chỉ là thông tin định danh nhà trọ; thay đổi hai trường này cần được duyệt lại. */
    public boolean changesCoreInfo(String newName, String newAddress) {
        return !name.equals(newName.trim()) || !address.equals(newAddress.trim());
    }

    public Property updateInfo(
        String newName,
        String newAddress,
        String newDescription,
        int newTotalRooms,
        PropertyApprovalStatus newStatus,
        String newRejectionReason
    ) {
        return new Property(id, newName.trim(), newAddress.trim(),
            newDescription == null || newDescription.isBlank() ? null : newDescription.trim(),
            newTotalRooms, ownerId, newStatus, newRejectionReason, createdAt);
    }

    public Property(
        UUID id,
        String name,
        String address,
        String description,
        int totalRooms,
        Instant createdAt
    ) {
        this(id, name, address, description, totalRooms, null, PropertyApprovalStatus.VERIFIED, null, createdAt);
    }

    /** Giới hạn theo cột {@code properties.name} (VARCHAR(200)); vượt quá thì DB báo lỗi và trang trả 500. */
    public static final int MAX_NAME_LENGTH = 200;

    /** Kiểm tra thông tin nhà trọ người dùng nhập; dùng chung cho tạo, sửa và khi dựng bản ghi. */
    public static void validateInfo(String name, String address, Integer totalRooms) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tên nhà trọ không được để trống.");
        }
        if (name.trim().length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Tên nhà trọ tối đa " + MAX_NAME_LENGTH + " ký tự.");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Địa chỉ nhà trọ không được để trống.");
        }
        if (totalRooms != null && totalRooms < 0) {
            throw new IllegalArgumentException("Tổng số phòng không được âm.");
        }
    }
}
