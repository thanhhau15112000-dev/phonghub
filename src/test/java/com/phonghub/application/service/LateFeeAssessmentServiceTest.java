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
        policy(true, 2);
        LocalDate today = DUE.plusDays(3);

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
        policy(true, 5);
        assertFalse(service.assessInvoice(invoice.getId(), DUE), "đúng hạn chưa quá hạn");
        assertFalse(service.assessInvoice(invoice.getId(), DUE.plusDays(5)), "còn trong ân hạn");

        policy(false, 0);
        assertFalse(service.assessInvoice(invoice.getId(), DUE.plusDays(30)), "chính sách tắt");

        policies.clear();
        assertFalse(service.assessInvoice(invoice.getId(), DUE.plusDays(30)), "chưa cấu hình chính sách");
        assertEquals(0, new BigDecimal("3500000").compareTo(total()));
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("A backfilled cycle is not fined until its due date passes")
    void invoiceIssuedAfterPaymentDayIsNotFinedOnIssue() {
        policy(true, 0);
        LocalDate issuedOn = LocalDate.of(2026, 10, 25);
        Invoice late = invoices.save(Invoice.issueMonthlyRent(
            contract, YearMonth.of(2026, 10), "PHLATE0003", List.of(), issuedOn));

        assertFalse(service.findInvoicesToAssess(issuedOn).contains(late.getId()));
        assertFalse(service.assessInvoice(late.getId(), issuedOn), "vừa tạo, chưa tới hạn");
        assertFalse(service.assessInvoice(late.getId(), late.getDueDate()), "đúng hạn chưa quá hạn");
        assertTrue(service.assessInvoice(late.getId(), late.getDueDate().plusDays(1)));
    }

    @Test
    @DisplayName("hóa đơn đã thanh toán đủ không bị phạt")
    void paidInvoiceIsSkipped() {
        policy(true, 0);
        Invoice stored = invoices.findById(invoice.getId()).orElseThrow();
        stored.applyPayment(new BigDecimal("3500000"), Instant.now());
        invoices.save(stored);

        assertFalse(service.assessInvoice(invoice.getId(), DUE.plusDays(10)));
        assertTrue(service.findInvoicesToAssess(DUE.plusDays(10)).isEmpty());
    }

    @Test
    @DisplayName("scheduler đếm số hóa đơn thay đổi và một hóa đơn lỗi không chặn các hóa đơn còn lại")
    void schedulerIsolatesFailures() {
        policy(true, 0);
        LateFeeScheduler scheduler = new LateFeeScheduler(service);
        LocalDate today = DUE.plusDays(1);

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
}
