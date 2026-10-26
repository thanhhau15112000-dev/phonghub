package com.phonghub.domain;

import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceItem;
import com.phonghub.domain.model.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutInvoiceRevisionTest {

    private final LocalDate today = LocalDate.of(2026, 10, 30);
    private final Contract contract = Contract.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
        new BigDecimal("7000000"), new BigDecimal("3500000"), today.minusDays(5), today.plusMonths(12), 5);
    private final UUID rateId = UUID.randomUUID();

    private InvoiceItem finalCharge(String amount) {
        return InvoiceItem.checkOutCharge("Điện", BigDecimal.ONE, new BigDecimal(amount), "kWh", rateId);
    }

    @Test
    void revisionPreservesPrepaidRentOtherChargesAndUnrelatedCredits() {
        contract.activate(Instant.parse("2026-10-24T17:00:00Z"));
        Invoice invoice = Invoice.issueMonthlyRent(contract, YearMonth.from(today), "PHREV00001");
        var monthlyMeter = InvoiceItem.charge("Điện tháng", BigDecimal.ONE, new BigDecimal("2000"),
            UUID.randomUUID(), rateId);
        var fixedService = InvoiceItem.charge("Wifi tháng", BigDecimal.ONE, new BigDecimal("20000"),
            null, UUID.randomUUID());
        invoice.addCharges(List.of(monthlyMeter, fixedService));
        invoice.applyPayment(invoice.getTotalAmount(), Instant.now());
        BigDecimal received = invoice.getPaidAmount();
        BigDecimal initialCredit = invoice.replaceWithCancellationFee(contract.billingCycleFor(today).orElseThrow(),
            new BigDecimal("1750000"), List.of(finalCharge("10000")), today);
        contract.addSettlementCredit(initialCredit);
        contract.addSettlementCredit(new BigDecimal("7000")); // a prepaid future cycle, independent of this invoice
        var dueDate = invoice.getDueDate();

        for (String amount : new String[] {"50000", "5000", "5000", "4000000", "10000"}) {
            BigDecimal previousCredit = invoice.getSettlementCreditAmount();
            invoice.reviseCheckOutUtilities(List.of(finalCharge(amount)));
            contract.reviseSettlementCredit(previousCredit, invoice.getSettlementCreditAmount());
            BigDecimal total = new BigDecimal("1772000").add(new BigDecimal(amount));
            BigDecimal expectedCredit = received.subtract(total).max(BigDecimal.ZERO);
            assertEquals(0, total.compareTo(invoice.getTotalAmount()));
            assertEquals(0, received.compareTo(invoice.getPaidAmount().add(invoice.getSettlementCreditAmount())));
            assertEquals(0, expectedCredit.add(new BigDecimal("7000")).compareTo(contract.getSettlementCreditAmount()));
            assertTrue(invoice.getItems().contains(monthlyMeter));
            assertTrue(invoice.getItems().contains(fixedService));
            assertEquals(4, invoice.getItems().size());
            assertEquals(dueDate, invoice.getDueDate());
        }
    }

    @Test
    void clearedCheckoutChargesPreserveReceivedMoneyAsCredit() {
        Invoice invoice = Invoice.issueCheckOutUtilities(contract, YearMonth.from(today), "PHREV00002", today.plusDays(3),
            List.of(finalCharge("30000")));
        invoice.applyPayment(new BigDecimal("30000"), Instant.now());
        invoice.reviseCheckOutUtilities(List.of());
        assertEquals(0, invoice.getTotalAmount().signum());
        assertEquals(0, invoice.getPaidAmount().signum());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        assertEquals(0, new BigDecimal("30000").compareTo(invoice.getSettlementCreditAmount()));
        assertTrue(invoice.getItems().isEmpty());
        invoice.reviseCheckOutUtilities(List.of(finalCharge("10000")));
        assertEquals(0, new BigDecimal("10000").compareTo(invoice.getPaidAmount()));
        assertEquals(0, new BigDecimal("20000").compareTo(invoice.getSettlementCreditAmount()));
    }

    @Test
    void rejectsUnrelatedInvoicesVoidedInvoicesAndInvalidCreditReplacement() {
        contract.activate(Instant.parse("2026-10-24T17:00:00Z"));
        var rent = Invoice.issueMonthlyRent(contract, YearMonth.from(contract.billingAnchorDate()), "PHREV00003");
        assertThrows(DomainException.class, () -> rent.reviseCheckOutUtilities(List.of()));
        var checkout = Invoice.issueCheckOutUtilities(contract, YearMonth.from(today), "PHREV00004", today.plusDays(3),
            List.of(finalCharge("30000")));
        assertThrows(IllegalArgumentException.class,
            () -> checkout.reviseCheckOutUtilities(List.of(InvoiceItem.of("Other", BigDecimal.ONE, null))));
        checkout.voidInvoice();
        assertThrows(DomainException.class, () -> checkout.reviseCheckOutUtilities(List.of()));
        assertThrows(DomainException.class, () -> contract.reviseSettlementCredit(BigDecimal.ONE, BigDecimal.ZERO));
    }
}
