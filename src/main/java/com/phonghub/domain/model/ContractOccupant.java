package com.phonghub.domain.model;

import java.time.LocalDate;
import java.util.UUID;

public record ContractOccupant(
    UUID id,
    UUID contractId,
    UUID tenantId,
    boolean isPrimary,
    LocalDate checkInDate,
    LocalDate checkOutDate
) {
    public ContractOccupant {
        if (id == null) {
            throw new IllegalArgumentException("Occupant id cannot be null");
        }
        if (contractId == null) {
            throw new IllegalArgumentException("Contract id cannot be null");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant id cannot be null");
        }
        if (checkInDate == null) {
            throw new IllegalArgumentException("Check in date cannot be null");
        }
        if (checkOutDate != null && checkOutDate.isBefore(checkInDate)) {
            throw new IllegalArgumentException("Ngày rời phòng không được trước ngày vào ở.");
        }
    }

    public ContractOccupant withCheckOutDate(LocalDate checkOutDate) {
        return new ContractOccupant(
            this.id,
            this.contractId,
            this.tenantId,
            this.isPrimary,
            this.checkInDate,
            checkOutDate
        );
    }
}
