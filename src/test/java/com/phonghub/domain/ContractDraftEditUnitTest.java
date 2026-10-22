package com.phonghub.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.phonghub.domain.exception.InvalidContractStateException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.ContractStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #89: sửa điều khoản và hủy hợp đồng nháp ở tầng domain. */
class ContractDraftEditUnitTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);

    private static Contract draft() {
        return Contract.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("1000000"),
            new BigDecimal("3000000"), START, START.plusYears(1), 5);
    }

    @Test
    @DisplayName("Sửa điều khoản nháp: đổi tiền, ngày và dời ngày vào ở của người ở trùng ngày bắt đầu cũ")
    void updatesTermsAndMovesCheckIn() {
        Contract contract = draft();
        UUID roommate = UUID.randomUUID();
        contract.addOccupant(roommate, false, START.plusDays(10));

        LocalDate newStart = START.plusDays(5);
        contract.updateDraftTerms(new BigDecimal("2000000"), new BigDecimal("3500000"), newStart,
            newStart.plusMonths(6), 10);

        assertEquals(new BigDecimal("2000000"), contract.getDepositAmount());
        assertEquals(new BigDecimal("3500000"), contract.getRentAmount());
        assertEquals(newStart, contract.getStartDate());
        assertEquals(newStart.plusMonths(6), contract.getEndDate());
        assertEquals(10, contract.getPaymentDay());
        ContractOccupant primary = contract.getOccupants().stream().filter(ContractOccupant::isPrimary).findFirst()
            .orElseThrow();
        assertEquals(newStart, primary.checkInDate(), "người thuê chính vào ở theo ngày bắt đầu mới");
        assertEquals(START.plusDays(10), contract.getOccupants().stream()
            .filter(o -> o.tenantId().equals(roommate)).findFirst().orElseThrow().checkInDate(),
            "người ở cùng có ngày vào riêng thì giữ nguyên");
    }

    @Test
    @DisplayName("Điều khoản sai bị từ chối và không thay đổi gì")
    void rejectsInvalidTerms() {
        Contract contract = draft();
        assertThrows(IllegalArgumentException.class, () -> contract.updateDraftTerms(
            new BigDecimal("-1"), new BigDecimal("3000000"), START, START.plusYears(1), 5));
        assertThrows(IllegalArgumentException.class, () -> contract.updateDraftTerms(
            BigDecimal.ZERO, BigDecimal.ZERO, START, START.plusYears(1), 5));
        assertThrows(IllegalArgumentException.class, () -> contract.updateDraftTerms(
            BigDecimal.ZERO, new BigDecimal("3000000"), START, START, 5));
        assertThrows(IllegalArgumentException.class, () -> contract.updateDraftTerms(
            BigDecimal.ZERO, new BigDecimal("3000000"), START, START.plusYears(1), 32));
        assertThrows(IllegalArgumentException.class, () -> contract.updateDraftTerms(
            BigDecimal.ZERO, new BigDecimal("3000000"), null, START.plusYears(1), 5));
        assertEquals(new BigDecimal("3000000"), contract.getRentAmount());
        assertEquals(START, contract.getStartDate());
    }

    @Test
    @DisplayName("Hủy nháp chuyển CANCELLED, không còn mở; hợp đồng đã kích hoạt hoặc đã hủy thì không sửa/hủy được")
    void cancelOnlyDraft() {
        Contract contract = draft();
        contract.cancelDraft();
        assertEquals(ContractStatus.CANCELLED, contract.getStatus());
        assertFalse(contract.isOpen());
        assertThrows(InvalidContractStateException.class, contract::cancelDraft);
        assertThrows(com.phonghub.domain.exception.DomainException.class, contract::activate);
        assertThrows(InvalidContractStateException.class, () -> contract.updateDraftTerms(
            BigDecimal.ZERO, new BigDecimal("3000000"), START, START.plusYears(1), 5));

        Contract active = draft();
        active.activate();
        assertThrows(InvalidContractStateException.class, active::cancelDraft);
        assertThrows(InvalidContractStateException.class, () -> active.updateDraftTerms(
            BigDecimal.ZERO, new BigDecimal("3000000"), START, START.plusYears(1), 5));
    }
}
