package com.phonghub.domain.model;

import com.phonghub.domain.exception.DomainException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Contract {
    private final UUID id;
    private final UUID propertyId;
    private final UUID roomId;
    private final UUID primaryTenantId;
    private final BigDecimal depositAmount;
    private final BigDecimal rentAmount;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final int paymentDay;
    private ContractStatus status;
    private final List<ContractOccupant> occupants;
    private final Instant createdAt;
    private Instant updatedAt;

    public Contract(
        UUID id,
        UUID propertyId,
        UUID roomId,
        UUID primaryTenantId,
        BigDecimal depositAmount,
        BigDecimal rentAmount,
        LocalDate startDate,
        LocalDate endDate,
        int paymentDay,
        ContractStatus status,
        List<ContractOccupant> occupants,
        Instant createdAt,
        Instant updatedAt
    ) {
        if (id == null) {
            throw new IllegalArgumentException("Contract id cannot be null");
        }
        if (propertyId == null) {
            throw new IllegalArgumentException("Property id cannot be null");
        }
        if (roomId == null) {
            throw new IllegalArgumentException("Room id cannot be null");
        }
        if (primaryTenantId == null) {
            throw new IllegalArgumentException("Primary tenant id cannot be null");
        }
        if (depositAmount == null || depositAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Deposit amount cannot be negative");
        }
        if (rentAmount == null || rentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Rent amount must be greater than zero");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date cannot be null");
        }
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        if (paymentDay < 1 || paymentDay > 31) {
            throw new IllegalArgumentException("Payment day must be between 1 and 31");
        }

        this.id = id;
        this.propertyId = propertyId;
        this.roomId = roomId;
        this.primaryTenantId = primaryTenantId;
        this.depositAmount = depositAmount;
        this.rentAmount = rentAmount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.paymentDay = paymentDay;
        this.status = status != null ? status : ContractStatus.DRAFT;
        this.occupants = new ArrayList<>();
        if (occupants != null) {
            this.occupants.addAll(occupants);
        }
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public static Contract create(
        UUID propertyId,
        UUID roomId,
        UUID primaryTenantId,
        BigDecimal depositAmount,
        BigDecimal rentAmount,
        LocalDate startDate,
        LocalDate endDate,
        int paymentDay
    ) {
        UUID contractId = UUID.randomUUID();
        Instant now = Instant.now();
        Contract contract = new Contract(
            contractId,
            propertyId,
            roomId,
            primaryTenantId,
            depositAmount,
            rentAmount,
            startDate,
            endDate,
            paymentDay,
            ContractStatus.DRAFT,
            new ArrayList<>(),
            now,
            now
        );
        // Add primary occupant by default
        contract.addOccupant(primaryTenantId, true, startDate);
        return contract;
    }

    public ContractOccupant addOccupant(UUID tenantId, boolean isPrimary, LocalDate checkInDate) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant id cannot be null");
        }
        boolean exists = occupants.stream().anyMatch(o -> o.tenantId().equals(tenantId));
        if (exists) {
            throw new DomainException("Tenant is already an occupant in this contract");
        }
        if (isPrimary && occupants.stream().anyMatch(ContractOccupant::isPrimary)) {
            throw new DomainException("Contract already has a primary occupant");
        }
        ContractOccupant occupant = new ContractOccupant(
            UUID.randomUUID(),
            this.id,
            tenantId,
            isPrimary,
            checkInDate != null ? checkInDate : this.startDate,
            null
        );
        this.occupants.add(occupant);
        this.updatedAt = Instant.now();
        return occupant;
    }

    public ContractOccupant checkOutOccupant(UUID tenantId, LocalDate checkOutDate) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant id cannot be null");
        }
        if (this.status != ContractStatus.ACTIVE) {
            throw new DomainException(String.format(
                "Chỉ có thể ghi nhận rời phòng cho hợp đồng đang có hiệu lực (ACTIVE). Trạng thái hiện tại: '%s'.",
                this.status
            ));
        }
        if (checkOutDate == null) {
            checkOutDate = LocalDate.now();
        }

        int targetIndex = -1;
        ContractOccupant target = null;
        for (int i = 0; i < this.occupants.size(); i++) {
            ContractOccupant o = this.occupants.get(i);
            if (o.tenantId().equals(tenantId)) {
                targetIndex = i;
                target = o;
                break;
            }
        }

        if (target == null) {
            throw new DomainException(String.format(
                "Người thuê '%s' không có trong danh sách người ở của hợp đồng này.",
                tenantId
            ));
        }

        if (target.isPrimary()) {
            throw new com.phonghub.domain.exception.PrimaryOccupantRemovalException(
                "Không thể gỡ người thuê chính khỏi hợp đồng. Muốn người thuê chính rời đi phải chấm dứt toàn bộ hợp đồng."
            );
        }

        if (target.checkOutDate() != null) {
            throw new DomainException(String.format(
                "Người thuê này đã được ghi nhận rời phòng vào ngày %s.",
                target.checkOutDate()
            ));
        }

        if (checkOutDate.isBefore(target.checkInDate())) {
            throw new IllegalArgumentException(String.format(
                "Ngày rời phòng (%s) không được trước ngày nhận phòng (%s).",
                checkOutDate, target.checkInDate()
            ));
        }

        ContractOccupant updated = target.withCheckOutDate(checkOutDate);
        this.occupants.set(targetIndex, updated);
        this.updatedAt = Instant.now();
        return updated;
    }

    public void activate() {
        if (this.status != ContractStatus.DRAFT) {
            throw new DomainException(String.format(
                "Contract %s cannot be activated because it is in status %s (must be DRAFT)",
                id, status
            ));
        }
        this.status = ContractStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void terminate() {
        if (this.status != ContractStatus.ACTIVE) {
            throw new DomainException(String.format(
                "Contract %s cannot be terminated because it is in status %s (must be ACTIVE)",
                id, status
            ));
        }
        this.status = ContractStatus.TERMINATED;
        this.updatedAt = Instant.now();
    }

    public void expire() {
        if (this.status != ContractStatus.ACTIVE) {
            throw new DomainException(String.format(
                "Contract %s cannot be expired because it is in status %s (must be ACTIVE)",
                id, status
            ));
        }
        this.status = ContractStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return this.status == ContractStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getPrimaryTenantId() {
        return primaryTenantId;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public BigDecimal getRentAmount() {
        return rentAmount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getPaymentDay() {
        return paymentDay;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public List<ContractOccupant> getOccupants() {
        return Collections.unmodifiableList(occupants);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contract contract = (Contract) o;
        return Objects.equals(id, contract.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
