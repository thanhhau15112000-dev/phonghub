package com.phonghub.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Tenant(
    UUID id,
    UUID userId,
    String fullName,
    String identityCardNumber,
    String phone,
    String email,
    String permanentAddress,
    Instant createdAt
) {
    public Tenant {
        if (id == null) {
            throw new IllegalArgumentException("Tenant id cannot be null");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Họ tên người thuê không được để trống.");
        }
        if (identityCardNumber == null || identityCardNumber.isBlank()) {
            throw new IllegalArgumentException("Số CCCD không được để trống.");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Số điện thoại không được để trống.");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Tenant updateInfo(
        String fullName,
        String identityCardNumber,
        String phone,
        String email,
        String permanentAddress
    ) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Họ và tên không được để trống");
        }
        if (identityCardNumber == null || identityCardNumber.isBlank()) {
            throw new IllegalArgumentException("Số CCCD không được để trống");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Số điện thoại không được để trống");
        }
        String cleanPhone = phone.trim();
        if (!cleanPhone.matches("^0\\d{9}$")) {
            throw new IllegalArgumentException("Số điện thoại phải gồm đúng 10 chữ số và bắt đầu bằng số 0 (VD: 0901234567)");
        }
        String cleanEmail = email != null && !email.isBlank() ? email.trim() : null;
        if (cleanEmail != null && !cleanEmail.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Định dạng email không hợp lệ: " + email);
        }
        String cleanAddress = permanentAddress != null && !permanentAddress.isBlank() ? permanentAddress.trim() : null;

        return new Tenant(
            this.id,
            this.userId,
            fullName.trim(),
            identityCardNumber.trim(),
            cleanPhone,
            cleanEmail,
            cleanAddress,
            this.createdAt
        );
    }

    public static Tenant create(
        UUID userId,
        String fullName,
        String identityCardNumber,
        String phone,
        String email,
        String permanentAddress
    ) {
        return new Tenant(
            UUID.randomUUID(),
            userId,
            fullName,
            identityCardNumber,
            phone,
            email,
            permanentAddress,
            Instant.now()
        );
    }

    /** Số CCCD thật đã bị xóa theo chính sách lưu trữ và thay bằng giá trị đánh dấu (vẫn duy nhất, không dùng để tra cứu). */
    public static final String PURGED_IDENTITY_PREFIX = "PURGED-";

    public static String purgedIdentityMarker(UUID tenantId) {
        return PURGED_IDENTITY_PREFIX + tenantId.toString().replace("-", "");
    }

    public boolean identityPurged() {
        return identityCardNumber != null && identityCardNumber.startsWith(PURGED_IDENTITY_PREFIX);
    }

    public String maskedIdentityCardNumber() {
        if (identityPurged()) {
            return "Đã xóa";
        }
        String normalized = identityCardNumber.trim();
        if (normalized.length() <= 4) {
            return "****";
        }
        return "****" + normalized.substring(normalized.length() - 4);
    }
}
