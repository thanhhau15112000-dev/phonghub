package com.phonghub.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.PaymentCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InvoiceUnitTest {

    private Contract contract(ContractStatus status, int paymentDay) {
        return new Contract(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("3000000"), new BigDecimal("3500000"),
            LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 15), paymentDay,
            status, List.of(), Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("Due date clamps payment day 31 to the last day of a short month")
    void dueDateClampsToMonthLength() {
        Invoice feb = Invoice.issueMonthlyRent(contract(ContractStatus.ACTIVE, 31), YearMonth.of(2026, 2), "PHAAAA2222");
        assertEquals(LocalDate.of(2026, 2, 28), feb.getDueDate());
        assertEquals(InvoiceStatus.ISSUED, feb.getStatus());
        assertEquals(0, new BigDecimal("3500000").compareTo(feb.getTotalAmount()));
    }

    @Test
    @DisplayName("Only ACTIVE contracts within their term can be invoiced")
    void issueRequiresActiveContractWithinTerm() {
        assertThrows(DomainException.class, () ->
            Invoice.issueMonthlyRent(contract(ContractStatus.DRAFT, 5), YearMonth.of(2026, 3), "PHAAAA2222"));
        assertThrows(DomainException.class, () ->
            Invoice.issueMonthlyRent(contract(ContractStatus.ACTIVE, 5), YearMonth.of(2025, 12), "PHAAAA2222"));
        assertThrows(DomainException.class, () ->
            Invoice.issueMonthlyRent(contract(ContractStatus.ACTIVE, 5), YearMonth.of(2027, 2), "PHAAAA2222"));
    }

    @Test
    @DisplayName("Partial then full payment moves ISSUED -> PARTIALLY_PAID -> PAID; paid invoice rejects more payments")
    void paymentLifecycle() {
        Invoice invoice = Invoice.issueMonthlyRent(contract(ContractStatus.ACTIVE, 5), YearMonth.of(2026, 3), "PHAAAA2222");

        invoice.applyPayment(new BigDecimal("1500000"), Instant.now());
        assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.getStatus());
        assertEquals(0, new BigDecimal("2000000").compareTo(invoice.remainingAmount()));

        invoice.applyPayment(new BigDecimal("2500000"), Instant.now());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        assertEquals(0, invoice.remainingAmount().signum());
        assertFalse(invoice.isPayable());
        assertThrows(DomainException.class, () -> invoice.applyPayment(BigDecimal.ONE, Instant.now()));
    }

    @Test
    @DisplayName("Due / overdue are derived from the due date only while unpaid")
    void dueAndOverdue() {
        Invoice invoice = Invoice.issueMonthlyRent(contract(ContractStatus.ACTIVE, 5), YearMonth.of(2026, 3), "PHAAAA2222");
        assertFalse(invoice.isDue(LocalDate.of(2026, 3, 4)));
        assertTrue(invoice.isDue(LocalDate.of(2026, 3, 5)));
        assertFalse(invoice.isOverdue(LocalDate.of(2026, 3, 5)));
        assertTrue(invoice.isOverdue(LocalDate.of(2026, 3, 6)));

        invoice.applyPayment(new BigDecimal("3500000"), Instant.now());
        assertFalse(invoice.isOverdue(LocalDate.of(2026, 3, 6)));
    }

    @Test
    @DisplayName("Payment code format and candidate extraction")
    void paymentCodeFormat() {
        String code = PaymentCode.generate();
        assertTrue(code.matches("PH[A-Z0-9]{8}"));
        assertEquals(List.of(code), List.copyOf(PaymentCode.findCandidates("chuyen khoan " + code.toLowerCase())));
        assertTrue(PaymentCode.findCandidates("PHONGHUBPHAB23CD45").contains("PHAB23CD45"));
        assertTrue(PaymentCode.findCandidates(null).isEmpty());
    }
}
