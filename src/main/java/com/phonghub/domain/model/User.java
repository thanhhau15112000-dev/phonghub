package com.phonghub.domain.model;

import java.time.Instant;
import java.util.UUID;

public record User(
    UUID id,
    String username,
    String email,
    String fullName,
    String phone,
    UserRole role,
    UserStatus status,
    boolean mustChangePassword,
    Instant createdAt
) {
    public enum UserStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED
    }

    public User(
        UUID id,
        String email,
        String fullName,
        String phone,
        UserRole role,
        UserStatus status,
        Instant createdAt
    ) {
        this(id, null, email, fullName, phone, role, status, false, createdAt);
    }

    public User {
        if (id == null) {
            throw new IllegalArgumentException("User id cannot be null");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name cannot be blank");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        if (status == null) {
            status = UserStatus.ACTIVE;
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
