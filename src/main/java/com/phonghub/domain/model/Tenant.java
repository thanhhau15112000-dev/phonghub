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
            throw new IllegalArgumentException("Tenant full name cannot be blank");
        }
        if (identityCardNumber == null || identityCardNumber.isBlank()) {
            throw new IllegalArgumentException("Identity card number cannot be blank");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone cannot be blank");
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
}
