package com.phonghub.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.in.scheduling.LateFeeScheduler;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryLateFeePolicyRepository;
import com.phonghub.application.port.in.LateFeeAssessmentUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.LateFeePolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #80: scheduler tính phạt idempotent theo cấu hình từng nhà trọ. */
class LateFeeAssessmentServiceTest {

    private static final LocalDate DUE = LocalDate.of(2026, 3, 8);

    private final UUID propertyId = UUID.randomUUID();
    private InMemoryInvoiceRepository invoices;
    private InMemoryLateFeePolicyRepository policies;
    private List<AuditPort.AuditEvent> events;
    private LateFeeAssessmentService service;
    private Invoice invoice;
    private Contract contract;

    @BeforeEach
    void setUp() {
        invoices = new InMemoryInvoiceRepository();
        policies = new InMemoryLateFeePolicyRepository();
        InMemoryContractRepository contracts = new InMemoryContractRepository();
        events = new ArrayList<>();
        service = new LateFeeAssessmentService(invoices, contracts, policies, events::add);

        contract = new Contract(
            UUID.randomUUID(), propertyId, UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("3000000"), new BigDecimal("3500000"), LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 5,
            ContractStatus.ACTIVE, List.of(), Instant.now(), Instant.now());
        contracts.save(contract);
        invoice = invoices.save(Invoice.issueMonthlyRent(contract, YearMonth.of(2026, 3), "PHLATE0002"));
        assertEquals(DUE, invoice.getDueDate());
    }

    private void policy(boolean enabled, int grace) {
        policies.save(new LateFeePolicy(propertyId, enabled, LateFeePolicy.FeeType.PERCENT, new BigDecimal("2"),
            grace, null, Instant.now(), null));
    }

    private BigDecimal total() {
        return invoices.findById(invoice.getId()).orElseThrow().getTotalAmount();
    }

    @Test
    @DisplayName("phạt tăng theo ngày quá hạn sau ân hạn; chạy lại cùng ngày không cộng dồn")
    void dailyFeeIsIdempotentForSameDay() {
        policy(true, 7);
        LocalDate today = DUE.plusDays(8);

        assertTrue(service.assessInvoice(invoice.getId(), today));
        assertFalse(service.assessInvoice(invoice.getId(), today));
        assertTrue(service.assessInvoice(invoice.getId(), today.plusDays(10)), "later days update the daily total");

        assertEquals(0, new BigDecimal("4270000").compareTo(total()));
        assertEquals(2, events.size());
        assertEquals("LATE_FEE_APPLIED", events.getLast().action());
        assertEquals(propertyId.toString(), events.getLast().details().get("propertyId"));
        assertEquals(11L, events.getLast().details().get("penaltyDays"));
        assertEquals("Phạt trả trễ (11 ngày)",
            invoices.findById(invoice.getId()).orElseThrow().lateFeeItem().orElseThrow().name());
    }

    @Test
    @DisplayName("chưa quá hạn, còn trong ân hạn hoặc chính sách tắt thì không phạt")
    void noFeeCases() {
        policy(true, 7);
        assertFalse(service.assessInvoice(invoice.getId(), DUE), "đúng hạn chưa quá hạn");
        assertFalse(service.assessInvoice(invoice.getId(), DUE.plusDays(7)), "đủ 7 ngày quá hạn vẫn chưa phạt");

        policy(false, 7);
        assertFalse(service.assessInvoice(invoice.getId(), DUE.plusDays(30)), "chính sách tắt");

        policies.clear();
        assertFalse(service.assessInvoice(invoice.getId(), DUE.plusDays(30)), "chưa cấu hình chính sách");
        assertEquals(0, new BigDecimal("3500000").compareTo(total()));
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("A backfilled cycle is not fined until its due date passes")
    void invoiceIssuedAfterPaymentDayIsNotFinedOnIssue() {
        policy(true, 7);
        LocalDate issuedOn = LocalDate.of(2026, 10, 25);
        Invoice late = invoices.save(Invoice.issueMonthlyRent(
            contract, YearMonth.of(2026, 10), "PHLATE0003", List.of(), issuedOn));

        assertFalse(service.findInvoicesToAssess(issuedOn).contains(late.getId()));
        assertFalse(service.assessInvoice(late.getId(), issuedOn), "vừa tạo, chưa tới hạn");
        assertFalse(service.assessInvoice(late.getId(), late.getDueDate()), "đúng hạn chưa quá hạn");
        assertFalse(service.assessInvoice(late.getId(), late.getDueDate().plusDays(7)), "chờ đủ 7 ngày quá hạn");
        assertTrue(service.assessInvoice(late.getId(), late.getDueDate().plusDays(8)));
    }

    @Test
    @DisplayName("hóa đơn đã thanh toán đủ không bị phạt")
    void paidInvoiceIsSkipped() {
        policy(true, 7);
        Invoice stored = invoices.findById(invoice.getId()).orElseThrow();
        stored.applyPayment(new BigDecimal("3500000"), Instant.now());
        invoices.save(stored);

        assertFalse(service.assessInvoice(invoice.getId(), DUE.plusDays(10)));
        assertTrue(service.findInvoicesToAssess(DUE.plusDays(10)).isEmpty());
    }

    @Test
    @DisplayName("scheduler đếm số hóa đơn thay đổi và một hóa đơn lỗi không chặn các hóa đơn còn lại")
    void schedulerIsolatesFailures() {
        policy(true, 7);
        LateFeeScheduler scheduler = new LateFeeScheduler(service);
        LocalDate today = DUE.plusDays(8);

        assertEquals(1, scheduler.run(today));
        assertEquals(0, scheduler.run(today), "chạy lại không thay đổi gì");

        LateFeeAssessmentUseCase flaky = new LateFeeAssessmentUseCase() {
            @Override
            public List<UUID> findInvoicesToAssess(LocalDate day) {
                return List.of(UUID.randomUUID(), invoice.getId());
            }

            @Override
            public boolean assessInvoice(UUID invoiceId, LocalDate day) {
                if (!invoiceId.equals(invoice.getId())) {
                    throw new IllegalStateException("boom");
                }
                return true;
            }
        };
        assertEquals(1, new LateFeeScheduler(flaky).run(today));
    }

    @Test
    @DisplayName("#234: phí sửa chữa quá hạn 3 ngày bị phạt 10% tổng tiền, đúng một lần dù chạy job nhiều lần")
    void maintenanceFeeGetsTenPercentOnce() {
        LocalDate issued = LocalDate.of(2026, 11, 26);
        Invoice fee = invoices.save(Invoice.issueMaintenanceFee(
            contract, UUID.randomUUID(), new BigDecimal("500000"), "PHFEE00001", issued));
        assertEquals(LocalDate.of(2026, 11, 29), fee.getDueDate());

        // Hết hạn ngày 29/11: chưa phạt.
        assertFalse(service.assessInvoice(fee.getId(), LocalDate.of(2026, 11, 29)));
        // Từ 30/11 là quá hạn: phạt 10% = 50.000, không phụ thuộc cấu hình phạt của nhà trọ (chưa bật).
        assertTrue(service.findInvoicesToAssess(LocalDate.of(2026, 11, 30)).contains(fee.getId()));
        assertTrue(service.assessInvoice(fee.getId(), LocalDate.of(2026, 11, 30)));
        assertEquals(0, new BigDecimal("550000").compareTo(invoices.findById(fee.getId()).orElseThrow().getTotalAmount()));
        assertEquals("Phạt trễ hạn phí sửa chữa (10%)",
            invoices.findById(fee.getId()).orElseThrow().lateFeeItem().orElseThrow().name());
        assertEquals("MAINTENANCE", events.get(events.size() - 1).details().get("invoiceType"));

        // Chạy lại nhiều lần trong ngày và các ngày sau: vẫn 550.000.
        assertFalse(service.assessInvoice(fee.getId(), LocalDate.of(2026, 11, 30)));
        assertFalse(service.assessInvoice(fee.getId(), LocalDate.of(2026, 12, 15)));
        assertEquals(0, new BigDecimal("550000").compareTo(invoices.findById(fee.getId()).orElseThrow().getTotalAmount()));
    }

    @Test
    @DisplayName("#234: đã trả một phần trước hạn, quá hạn vẫn phạt 10% trên tổng tiền sửa chữa")
    void maintenanceFeePenaltyIgnoresPartialPayment() {
        Invoice fee = Invoice.issueMaintenanceFee(
            contract, UUID.randomUUID(), new BigDecimal("500000"), "PHFEE00002", LocalDate.of(2026, 11, 26));
        fee.applyPayment(new BigDecimal("200000"), Instant.now());
        invoices.save(fee);

        assertTrue(service.assessInvoice(fee.getId(), LocalDate.of(2026, 11, 30)));
        Invoice saved = invoices.findById(fee.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("50000").compareTo(saved.lateFeeItem().orElseThrow().totalAmount()));
        assertEquals(0, new BigDecimal("350000").compareTo(saved.remainingAmount()));
    }

    @Test
    @DisplayName("#234: phí phát hành trước ngày áp dụng giữ nguyên, không bị phạt")
    void legacyMaintenanceFeeIsNotPenalised() {
        Invoice fee = invoices.save(Invoice.issueMaintenanceFee(
            contract, UUID.randomUUID(), new BigDecimal("500000"), "PHFEE00003", LocalDate.of(2026, 10, 20)));
        assertFalse(service.assessInvoice(fee.getId(), LocalDate.of(2026, 12, 15)));
    }

    @Test
    @DisplayName("#234: phí còn trong hạn 3 ngày không gộp vào hóa đơn tháng; quá hạn và đã phạt mới gộp được")
    void feeIsRollableOnlyAfterPenalty() {
        LocalDate issued = LocalDate.of(2026, 11, 26);
        Invoice fee = invoices.save(Invoice.issueMaintenanceFee(
            contract, UUID.randomUUID(), new BigDecimal("500000"), "PHFEE00004", issued));
        assertFalse(fee.isRollableFee(LocalDate.of(2026, 11, 28)));
        assertFalse(fee.isRollableFee(LocalDate.of(2026, 11, 30)));
        service.assessInvoice(fee.getId(), LocalDate.of(2026, 11, 30));
        assertTrue(invoices.findById(fee.getId()).orElseThrow().isRollableFee(LocalDate.of(2026, 12, 1)));
    }
}
