package com.phonghub.domain;

import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContractLifecycleUnitTest {

    private final UUID propertyId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();
    private final UUID primaryTenantId = UUID.randomUUID();

    @Test
    @DisplayName("Contract creation initializes DRAFT status and primary occupant")
    void testContractCreation() {
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusMonths(6);

        Contract contract = Contract.create(
            propertyId,
            roomId,
            primaryTenantId,
            new BigDecimal("3000000"),
            new BigDecimal("3000000"),
            start,
            end,
            5
        );

        assertEquals(ContractStatus.DRAFT, contract.getStatus());
        assertFalse(contract.isActive());
        assertEquals(1, contract.getOccupants().size());
        assertTrue(contract.getOccupants().getFirst().isPrimary());
        assertEquals(primaryTenantId, contract.getOccupants().getFirst().tenantId());
    }

    @Test
    @DisplayName("Contract creation validates date range and amounts")
    void testContractValidation() {
        LocalDate start = LocalDate.now();

        // End date before start date
        assertThrows(IllegalArgumentException.class, () ->
            Contract.create(propertyId, roomId, primaryTenantId, BigDecimal.ZERO, new BigDecimal("100"), start, start.minusDays(1), 5)
        );

        // Negative rent
        assertThrows(IllegalArgumentException.class, () ->
            Contract.create(propertyId, roomId, primaryTenantId, BigDecimal.ZERO, new BigDecimal("-100"), start, start.plusDays(30), 5)
        );

        // Invalid payment day
        assertThrows(IllegalArgumentException.class, () ->
            Contract.create(propertyId, roomId, primaryTenantId, BigDecimal.ZERO, new BigDecimal("100"), start, start.plusDays(30), 32)
        );
    }

    @Test
    @DisplayName("Activating DRAFT contract changes status to ACTIVE")
    void testContractActivation() {
        Contract contract = Contract.create(
            propertyId,
            roomId,
            primaryTenantId,
            new BigDecimal("3000000"),
            new BigDecimal("3000000"),
            LocalDate.now(),
            LocalDate.now().plusMonths(6),
            5
        );

        contract.activate();
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());
        assertTrue(contract.isActive());

        // Cannot activate again
        assertThrows(DomainException.class, contract::activate);
    }

    @Test
    @DisplayName("Terminating ACTIVE contract changes status to TERMINATED")
    void testContractTermination() {
        Contract contract = Contract.create(
            propertyId,
            roomId,
            primaryTenantId,
            new BigDecimal("3000000"),
            new BigDecimal("3000000"),
            LocalDate.now(),
            LocalDate.now().plusMonths(6),
            5
        );

        // Cannot terminate DRAFT
        assertThrows(DomainException.class, contract::terminate);

        contract.activate();
        contract.terminate();
        assertEquals(ContractStatus.TERMINATED, contract.getStatus());
        assertFalse(contract.isActive());
    }

    @Test
    @DisplayName("Can add additional occupant to contract")
    void testAddOccupant() {
        Contract contract = Contract.create(
            propertyId,
            roomId,
            primaryTenantId,
            new BigDecimal("3000000"),
            new BigDecimal("3000000"),
            LocalDate.now(),
            LocalDate.now().plusMonths(6),
            5
        );

        UUID roommateId = UUID.randomUUID();
        contract.addOccupant(roommateId, false, LocalDate.now());
        assertEquals(2, contract.getOccupants().size());

        // Cannot add same occupant twice
        assertThrows(DomainException.class, () -> contract.addOccupant(roommateId, false, LocalDate.now()));

        // Cannot add a second primary occupant
        UUID anotherId = UUID.randomUUID();
        assertThrows(DomainException.class, () -> contract.addOccupant(anotherId, true, LocalDate.now()));
    }
}
