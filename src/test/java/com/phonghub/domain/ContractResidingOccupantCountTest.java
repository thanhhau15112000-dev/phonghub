package com.phonghub.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.ContractStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Hàm đếm chỗ duy nhất của hợp đồng: mọi đường thêm người và màn hình "Còn N chỗ" dùng chung nó. */
class ContractResidingOccupantCountTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private final UUID contractId = UUID.randomUUID();
    private final UUID primary = UUID.randomUUID();

    private Contract contract(ContractStatus status, List<ContractOccupant> occupants) {
        return new Contract(contractId, UUID.randomUUID(), UUID.randomUUID(), primary, new BigDecimal("1000000"),
            new BigDecimal("2000000"), TODAY.minusMonths(1), TODAY.plusMonths(6), 5, status, occupants,
            Instant.now(), Instant.now());
    }

    private ContractOccupant row(UUID tenantId, boolean isPrimary, LocalDate checkOut) {
        return new ContractOccupant(UUID.randomUUID(), contractId, tenantId, isPrimary, TODAY.minusMonths(1), checkOut);
    }

    @Test
    @DisplayName("The primary tenant row counts once")
    void primaryRowCountsOnce() {
        assertEquals(1, contract(ContractStatus.ACTIVE, List.of(row(primary, true, null))).residingOccupantCount(TODAY));
    }

    @Test
    @DisplayName("A legacy active contract without occupant rows still counts its primary tenant")
    void legacyContractCountsPrimary() {
        assertEquals(1, contract(ContractStatus.ACTIVE, List.of()).residingOccupantCount(TODAY));
        assertEquals(1, contract(ContractStatus.EXPIRED, List.of()).residingOccupantCount(TODAY));
    }

    @Test
    @DisplayName("A draft contract has nobody living in the room yet")
    void draftContractHasNoResidents() {
        assertEquals(0, contract(ContractStatus.DRAFT, List.of()).residingOccupantCount(TODAY));
    }

    @Test
    @DisplayName("Checked-out occupants are not counted; occupants leaving later still are")
    void checkedOutOccupantsAreNotCounted() {
        UUID gone = UUID.randomUUID();
        UUID leavingLater = UUID.randomUUID();
        Contract contract = contract(ContractStatus.ACTIVE, List.of(
            row(primary, true, null), row(gone, false, TODAY.minusDays(1)), row(leavingLater, false, TODAY.plusDays(3))));

        assertEquals(2, contract.residingOccupantCount(TODAY));
    }

    @Test
    @DisplayName("When only a later occupant row exists, the primary tenant is still counted in addition")
    void primaryAddedWhenItsRowIsMissing() {
        Contract contract = contract(ContractStatus.ACTIVE, List.of(row(UUID.randomUUID(), false, null)));

        assertEquals(2, contract.residingOccupantCount(TODAY));
    }
}
