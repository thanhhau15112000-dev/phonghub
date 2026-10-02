package com.phonghub.application.port.in;

import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractUseCase {
    Contract createContract(CreateContractCommand command);
    Contract activateContract(UUID contractId);
    Contract terminateContract(UUID contractId, boolean requiresMaintenance);
    Contract getContract(UUID contractId);
    List<Contract> listContractsForProperty(UUID propertyId);
    Optional<Contract> getActiveContractForTenant(UUID tenantUserId);
    List<Contract> listContractsForTenant(UUID tenantUserId);
    ContractOccupant addOccupant(AddOccupantCommand command);
    ContractOccupant checkOutOccupant(CheckOutOccupantCommand command);

    record CheckOutOccupantCommand(
        UUID contractId,
        UUID tenantId,
        LocalDate checkOutDate
    ) {
        public CheckOutOccupantCommand {
            if (contractId == null) {
                throw new IllegalArgumentException("Contract ID cannot be null");
            }
            if (tenantId == null) {
                throw new IllegalArgumentException("Tenant ID cannot be null");
            }
            if (checkOutDate == null) {
                checkOutDate = LocalDate.now();
            }
        }
    }

    record AddOccupantCommand(
        UUID contractId,
        String fullName,
        String identityCardNumber,
        String phone,
        String email,
        String permanentAddress,
        LocalDate checkInDate,
        boolean createAccount
    ) {
        public AddOccupantCommand {
            if (contractId == null) {
                throw new IllegalArgumentException("Contract ID cannot be null");
            }
            if (fullName == null || fullName.isBlank()) {
                throw new IllegalArgumentException("Họ và tên không được để trống");
            }
            if (identityCardNumber == null || identityCardNumber.isBlank()) {
                throw new IllegalArgumentException("Số CCCD không được để trống");
            }
            if (phone == null || phone.isBlank()) {
                throw new IllegalArgumentException("Số điện thoại không được để trống");
            }
            if (checkInDate == null) {
                checkInDate = LocalDate.now();
            }
        }
    }

    record CreateContractCommand(
        UUID propertyId,
        UUID roomId,
        String primaryTenantFullName,
        String primaryTenantIdCard,
        String primaryTenantPhone,
        String primaryTenantEmail,
        UUID primaryTenantUserId, // optional if user account already exists
        BigDecimal depositAmount,
        BigDecimal rentAmount,
        LocalDate startDate,
        LocalDate endDate,
        int paymentDay
    ) {
        public CreateContractCommand {
            if (propertyId == null) {
                throw new IllegalArgumentException("Property id cannot be null");
            }
            if (roomId == null) {
                throw new IllegalArgumentException("Room id cannot be null");
            }
            if (primaryTenantFullName == null || primaryTenantFullName.isBlank()) {
                throw new IllegalArgumentException("Primary tenant full name cannot be blank");
            }
            if (primaryTenantIdCard == null || primaryTenantIdCard.isBlank()) {
                throw new IllegalArgumentException("Primary tenant ID card cannot be blank");
            }
            if (primaryTenantPhone == null || primaryTenantPhone.isBlank()) {
                throw new IllegalArgumentException("Primary tenant phone cannot be blank");
            }
            if (depositAmount == null || depositAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Deposit amount cannot be negative");
            }
            if (rentAmount == null || rentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Rent amount must be greater than zero");
            }
            if (startDate == null || endDate == null) {
                throw new IllegalArgumentException("Dates cannot be null");
            }
            if (!endDate.isAfter(startDate)) {
                throw new IllegalArgumentException("End date must be after start date");
            }
            if (paymentDay < 1 || paymentDay > 31) {
                throw new IllegalArgumentException("Payment day must be between 1 and 31");
            }
        }
    }
}
