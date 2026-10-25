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
        return contract(status, paymentDay, LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 15));
    }

    private Contract contract(ContractStatus status, int paymentDay, LocalDate startDate, LocalDate endDate) {
        return new Contract(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("3000000"), new BigDecimal("3500000"),
            startDate, endDate, paymentDay,
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
    @DisplayName("First month's due date is not before the contract start date when payment day has passed")
    void firstMonthDueDateCannotPrecedeContractStart() {
        LocalDate startDate = LocalDate.of(2026, 10, 25);
        Contract contract = contract(ContractStatus.ACTIVE, 5, startDate, LocalDate.of(2027, 10, 24));

        Invoice invoice = Invoice.issueMonthlyRent(contract, YearMonth.of(2026, 10), "PHAAAA2222");

        assertEquals(startDate, invoice.getDueDate());
        assertFalse(invoice.isOverdue(startDate));
        assertTrue(invoice.isOverdue(startDate.plusDays(1)));
        assertEquals(0, new BigDecimal("3500000").compareTo(invoice.getTotalAmount()), "first-month rent is not prorated");
    }

    @Test
    @DisplayName("First month's configured payment day remains when it is on or after the contract start")
    void firstMonthKeepsPaymentDayWhenItHasNotPassed() {
        Contract contract = contract(
            ContractStatus.ACTIVE, 5, LocalDate.of(2026, 10, 2), LocalDate.of(2027, 10, 1));

        Invoice invoice = Invoice.issueMonthlyRent(contract, YearMonth.of(2026, 10), "PHAAAA2222");

        assertEquals(LocalDate.of(2026, 10, 5), invoice.getDueDate());
    }

    @Test
    @DisplayName("First month's due date is clamped to the contract start when a short-month payment day has passed")
    void shortMonthFirstDueDateCannotPrecedeContractStart() {
        LocalDate startDate = LocalDate.of(2026, 2, 20);
        Contract contract = contract(ContractStatus.ACTIVE, 31, startDate, LocalDate.of(2027, 2, 19));

        Invoice invoice = Invoice.issueMonthlyRent(contract, YearMonth.of(2026, 2), "PHAAAA2222");

        assertEquals(LocalDate.of(2026, 2, 28), invoice.getDueDate());
        assertFalse(invoice.isOverdue(startDate));
    }

    private static Invoice issueOn(Contract contract, YearMonth period, LocalDate issuedOn) {
        return Invoice.issueMonthlyRent(contract, period, "PHAAAA2222", List.of(), issuedOn);
    }

    @Test
    @DisplayName("#208: a period issued after its payment day is due three days after issue, not already overdue")
    void issuedAfterPaymentDayGetsThreeDays() {
        // Hợp đồng nhập cho khách đã ở từ 01/10, ngày thanh toán 5, kỳ 10/2026 tạo ngày 25/10.
        Contract contract = contract(ContractStatus.ACTIVE, 5, LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30));
        LocalDate issuedOn = LocalDate.of(2026, 10, 25);

        Invoice invoice = issueOn(contract, YearMonth.of(2026, 10), issuedOn);

        assertEquals(LocalDate.of(2026, 10, 28), invoice.getDueDate());
        assertFalse(invoice.isOverdue(issuedOn));
        assertFalse(invoice.isOverdue(LocalDate.of(2026, 10, 28)), "due day itself is not overdue");
        assertTrue(invoice.isOverdue(LocalDate.of(2026, 10, 29)));
    }

    @Test
    @DisplayName("#208: a first period issued a few days after the start date is not overdue on issue")
    void firstPeriodIssuedAfterStartDate() {
        Contract contract = contract(ContractStatus.ACTIVE, 5, LocalDate.of(2026, 10, 25), LocalDate.of(2027, 10, 24));

        Invoice invoice = issueOn(contract, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 28));

        assertEquals(LocalDate.of(2026, 10, 31), invoice.getDueDate());
        assertFalse(invoice.isOverdue(LocalDate.of(2026, 10, 28)));
    }

    @Test
    @DisplayName("#208: issuing on the start date leaves three days to pay instead of zero")
    void issuedOnStartDateLeavesThreeDays() {
        LocalDate startDate = LocalDate.of(2026, 10, 25);
        Contract contract = contract(ContractStatus.ACTIVE, 5, startDate, LocalDate.of(2027, 10, 24));

        Invoice invoice = issueOn(contract, YearMonth.of(2026, 10), startDate);

        assertEquals(LocalDate.of(2026, 10, 28), invoice.getDueDate());
    }

    @Test
    @DisplayName("#208: a payment day more than three days after issue is kept as the due date")
    void laterPaymentDayIsKept() {
        Contract contract = contract(ContractStatus.ACTIVE, 5, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 7, 31));

        // Job tạo kỳ 11/2026 vào ngày cuối tháng 10: hạn vẫn là ngày thanh toán 05/11.
        Invoice invoice = issueOn(contract, YearMonth.of(2026, 11), LocalDate.of(2026, 10, 31));

        assertEquals(LocalDate.of(2026, 11, 5), invoice.getDueDate());
    }

    @Test
    @DisplayName("#208: the three-day floor can move the due date into the next month for a short month")
    void floorCrossesShortMonthEnd() {
        Contract contract = contract(ContractStatus.ACTIVE, 31, LocalDate.of(2026, 2, 20), LocalDate.of(2027, 2, 19));

        Invoice invoice = issueOn(contract, YearMonth.of(2026, 2), LocalDate.of(2026, 2, 27));

        assertEquals(LocalDate.of(2026, 3, 2), invoice.getDueDate());
        assertFalse(invoice.isOverdue(LocalDate.of(2026, 2, 28)));
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

        assertThrows(DomainException.class, () -> invoice.applyPayment(new BigDecimal("2500000"), Instant.now()));
        assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.getStatus());
        invoice.applyPayment(new BigDecimal("2000000"), Instant.now());
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
    @DisplayName("Maintenance fee invoice: ACTIVE contract only, due in 7 days, voidable only while unpaid")
    void maintenanceFeeInvoice() {
        UUID ticketId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 3, 10);

        assertThrows(DomainException.class, () -> Invoice.issueMaintenanceFee(
            contract(ContractStatus.DRAFT, 5), ticketId, new BigDecimal("200000"), "PHAAAA2222", today));
        assertThrows(IllegalArgumentException.class, () -> Invoice.issueMaintenanceFee(
            contract(ContractStatus.ACTIVE, 5), ticketId, BigDecimal.ZERO, "PHAAAA2222", today));

        Invoice fee = Invoice.issueMaintenanceFee(
            contract(ContractStatus.ACTIVE, 5), ticketId, new BigDecimal("200000"), "PHAAAA2222", today);
        assertEquals(com.phonghub.domain.model.InvoiceType.MAINTENANCE, fee.getType());
        assertEquals(ticketId, fee.getTicketId());
        assertEquals(LocalDate.of(2026, 3, 17), fee.getDueDate());
        assertEquals(0, fee.getRentAmount().signum());

        fee.applyPayment(new BigDecimal("50000"), Instant.now());
        assertThrows(DomainException.class, fee::voidInvoice);

        Invoice unpaid = Invoice.issueMaintenanceFee(
            contract(ContractStatus.ACTIVE, 5), ticketId, new BigDecimal("200000"), "PHBBBB3333", today);
        unpaid.voidInvoice();
        assertEquals(InvoiceStatus.VOIDED, unpaid.getStatus());
        assertFalse(unpaid.isPayable());
    }

    @Test
    @DisplayName("Month-end invoice itemizes rent plus unpaid repair fees; fees paid or part-paid are never rolled in")
    void monthEndInvoiceRollsUnpaidFees() {
        Contract contract = contract(ContractStatus.ACTIVE, 5);
        LocalDate today = LocalDate.of(2026, 3, 10);
        Invoice unpaid = Invoice.issueMaintenanceFee(contract, UUID.randomUUID(), new BigDecimal("200000"), "PHAAAA2222", today);
        Invoice paid = Invoice.issueMaintenanceFee(contract, UUID.randomUUID(), new BigDecimal("100000"), "PHBBBB3333", today);
        paid.applyPayment(new BigDecimal("100000"), Instant.now());
        Invoice partial = Invoice.issueMaintenanceFee(contract, UUID.randomUUID(), new BigDecimal("300000"), "PHCCCC4444", today);
        partial.applyPayment(new BigDecimal("50000"), Instant.now());

        assertTrue(unpaid.isRollableFee());
        assertFalse(paid.isRollableFee());
        assertFalse(partial.isRollableFee());

        Invoice monthly = Invoice.issueMonthlyRent(
            contract, YearMonth.of(2026, 3), "PHDDDD5555", List.of(new Invoice.RolledFee(unpaid, "Vỡ kính")));

        assertEquals(0, new BigDecimal("3700000").compareTo(monthly.getTotalAmount()));
        assertEquals(0, new BigDecimal("3500000").compareTo(monthly.getRentAmount()));
        assertEquals(2, monthly.getItems().size());
        assertEquals("Tiền thuê phòng tháng 03/2026", monthly.getItems().get(0).name());
        assertEquals("Phí sửa chữa: Vỡ kính", monthly.getItems().get(1).name());
        assertEquals(unpaid.getId(), monthly.getItems().get(1).sourceInvoiceId());
        assertEquals(0, new BigDecimal("200000").compareTo(monthly.getItems().get(1).totalAmount()));

        // Khoản đã gộp không còn thanh toán riêng
        assertTrue(unpaid.isConsolidated());
        assertEquals(monthly.getId(), unpaid.getConsolidatedIntoInvoiceId());
        assertFalse(unpaid.isPayable());
        assertThrows(DomainException.class, () -> unpaid.applyPayment(BigDecimal.ONE, Instant.now()));

        // Khoản đã trả / trả một phần không gộp được
        assertThrows(DomainException.class, () -> Invoice.issueMonthlyRent(
            contract, YearMonth.of(2026, 4), "PHEEEE6666", List.of(new Invoice.RolledFee(paid, "x"))));
        assertThrows(DomainException.class, () -> Invoice.issueMonthlyRent(
            contract, YearMonth.of(2026, 4), "PHFFFF7777", List.of(new Invoice.RolledFee(partial, "x"))));
        // Không gộp hai lần
        assertThrows(DomainException.class, () -> Invoice.issueMonthlyRent(
            contract, YearMonth.of(2026, 4), "PHGGGG8888", List.of(new Invoice.RolledFee(unpaid, "x"))));
    }

    @Test
    @DisplayName("Month invoice without fees has a single rent line")
    void monthInvoiceWithoutFees() {
        Invoice monthly = Invoice.issueMonthlyRent(contract(ContractStatus.ACTIVE, 5), YearMonth.of(2026, 3), "PHAAAA2222");
        assertEquals(1, monthly.getItems().size());
        assertEquals(0, new BigDecimal("3500000").compareTo(monthly.getTotalAmount()));
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
