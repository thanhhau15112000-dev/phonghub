package com.phonghub.adapter.in.web.api.dto;

import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.ContractStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ContractResponse(
    UUID id,
    UUID propertyId,
    UUID roomId,
    UUID primaryTenantId,
    BigDecimal rentAmount,
    BigDecimal depositAmount,
    LocalDate startDate,
    LocalDate endDate,
    int paymentDay,
    ContractStatus status,
    List<ContractOccupantResponse> occupants,
    Instant createdAt,
    Instant updatedAt
) {
    public record ContractOccupantResponse(
        UUID id,
        UUID contractId,
        UUID tenantId,
        boolean isPrimary,
        LocalDate checkInDate,
        LocalDate checkOutDate
    ) {
        public static ContractOccupantResponse from(ContractOccupant occupant) {
            return new ContractOccupantResponse(
                occupant.id(),
                occupant.contractId(),
                occupant.tenantId(),
                occupant.isPrimary(),
                occupant.checkInDate(),
                occupant.checkOutDate()
            );
        }
    }

    public static ContractResponse from(Contract contract) {
        List<ContractOccupantResponse> occupantResponses = contract.getOccupants() != null
            ? contract.getOccupants().stream().map(ContractOccupantResponse::from).toList()
            : List.of();

        return new ContractResponse(
            contract.getId(),
            contract.getPropertyId(),
            contract.getRoomId(),
            contract.getPrimaryTenantId(),
            contract.getRentAmount(),
            contract.getDepositAmount(),
            contract.getStartDate(),
            contract.getEndDate(),
            contract.getPaymentDay(),
            contract.getStatus(),
            occupantResponses,
            contract.getCreatedAt(),
            contract.getUpdatedAt()
        );
    }
}
