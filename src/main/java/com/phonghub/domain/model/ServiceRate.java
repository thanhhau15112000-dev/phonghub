package com.phonghub.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Đơn giá một loại dịch vụ của nhà trọ (điện, nước, rác, wifi...). */
public record ServiceRate(
    UUID id,
    UUID propertyId,
    String serviceName,
    ChargeType chargeType,
    BigDecimal pricePerUnit,
    Instant createdAt,
    UUID createdBy,
    Instant updatedAt,
    UUID updatedBy
) {
    public ServiceRate {
        if (id == null || propertyId == null || chargeType == null) {
            throw new IllegalArgumentException("Service rate requires id, property and charge type");
        }
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("Tên dịch vụ không được để trống.");
        }
        if (pricePerUnit == null || pricePerUnit.signum() < 0) {
            throw new IllegalArgumentException("Đơn giá không được âm.");
        }
        serviceName = serviceName.trim();
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public ServiceRate update(String newName, ChargeType newType, BigDecimal newPrice, UUID actorId) {
        return new ServiceRate(id, propertyId, newName, newType, newPrice, createdAt, createdBy, Instant.now(),
            actorId);
    }
}
