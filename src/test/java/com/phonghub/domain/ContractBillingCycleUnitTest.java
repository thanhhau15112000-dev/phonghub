package com.phonghub.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.domain.model.BillingCycle;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractBillingCycleUnitTest {

    private static Contract contract(ContractStatus status, LocalDate start, LocalDate end, Instant activatedAt) {
        return new Contract(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("3000000"), new BigDecimal("3500000"), start, end, 5, status, List.of(),
            activatedAt, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("Activation instant is converted to the Vietnam local date")
    void activationDateUsesVietnamZone() {
        Contract contract = contract(ContractStatus.DRAFT, LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30), null);
        contract.activate(Instant.parse("2026-10-24T17:00:00Z"));

        assertEquals(LocalDate.of(2026, 10, 25), contract.billingAnchorDate());
        assertEquals(LocalDate.of(2026, 10, 25), contract.billingCycle(YearMonth.of(2026, 10)).orElseThrow().startDate());
    }

    @Test
    @DisplayName("Legacy contracts without activation time keep startDate as their billing anchor")
    void legacyContractFallsBackToStartDate() {
        Contract contract = contract(ContractStatus.ACTIVE, LocalDate.of(2026, 1, 31), LocalDate.of(2027, 1, 30), null);

        assertEquals(LocalDate.of(2026, 1, 31), contract.billingAnchorDate());
        assertEquals(LocalDate.of(2026, 2, 28), contract.billingCycle(YearMonth.of(2026, 2)).orElseThrow().startDate());
        assertEquals(LocalDate.of(2026, 3, 31), contract.billingCycle(YearMonth.of(2026, 3)).orElseThrow().startDate());
    }

    @Test
    @DisplayName("A 12-cycle term starts on the activation day and ends the day before its anniversary")
    void twelveCyclesAreAnchoredToActivation() {
        Contract contract = contract(ContractStatus.ACTIVE, LocalDate.of(2026, 10, 25), LocalDate.of(2027, 10, 24),
            Instant.parse("2026-10-24T17:00:00Z"));

        List<BillingCycle> cycles = contract.billingCycles();

        assertEquals(12, cycles.size());
        assertEquals(new BillingCycle(1, LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 24)), cycles.getFirst());
        assertEquals(new BillingCycle(12, LocalDate.of(2027, 9, 25), LocalDate.of(2027, 10, 24)), cycles.getLast());
        assertTrue(contract.billingCycle(YearMonth.of(2027, 10)).isEmpty(), "no cycle starts after endDate");
    }

    @Test
    @DisplayName("Leap-year February clamps the cycle date and the original anchor day returns in March")
    void leapYearClamping() {
        Contract contract = contract(ContractStatus.ACTIVE, LocalDate.of(2028, 1, 31), LocalDate.of(2028, 4, 30), null);

        assertEquals(LocalDate.of(2028, 2, 29), contract.billingCycle(YearMonth.of(2028, 2)).orElseThrow().startDate());
        assertEquals(LocalDate.of(2028, 3, 31), contract.billingCycle(YearMonth.of(2028, 3)).orElseThrow().startDate());
        assertFalse(contract.billingCycle(YearMonth.of(2027, 12)).isPresent());
    }
}
