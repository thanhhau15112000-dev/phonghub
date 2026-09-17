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
