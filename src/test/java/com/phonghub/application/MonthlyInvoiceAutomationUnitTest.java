package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.in.scheduling.MonthlyInvoiceScheduler;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.application.port.in.MonthlyInvoiceUseCase;
import com.phonghub.application.service.MonthlyInvoiceService;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.MaintenanceCause;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceTicket;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MonthlyInvoiceAutomationUnitTest {

    private InMemoryContractRepository contractRepo;
    private InMemoryInvoiceRepository invoiceRepo;
    private InMemoryMaintenanceTicketRepository ticketRepo;
    private MonthlyInvoiceService service;
    private MonthlyInvoiceScheduler scheduler;

    @BeforeEach
    void setUp() {
        contractRepo = new InMemoryContractRepository();
        invoiceRepo = new InMemoryInvoiceRepository();
        ticketRepo = new InMemoryMaintenanceTicketRepository();
        service = new MonthlyInvoiceService(contractRepo, invoiceRepo, ticketRepo);
        scheduler = new MonthlyInvoiceScheduler(service);
    }

    private Contract contract(ContractStatus status, LocalDate start, LocalDate end) {
        Contract c = new Contract(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("3000000"), new BigDecimal("3500000"), start, end, 5,
            status, List.of(), Instant.now(), Instant.now()
        );
        contractRepo.save(c);
        return c;
    }

    private List<Invoice> rentInvoices(Contract c) {
        return invoiceRepo.findByContractId(c.getId()).stream().filter(i -> i.getType() == InvoiceType.RENT).toList();
    }

    @Test
    @DisplayName("Periods to ensure: current month always; next month only on the last day of the month")
    void periods() {
        assertEquals(List.of(YearMonth.of(2026, 10)), MonthlyInvoiceScheduler.periodsToEnsure(LocalDate.of(2026, 10, 2)));
        assertEquals(List.of(YearMonth.of(2026, 10)), MonthlyInvoiceScheduler.periodsToEnsure(LocalDate.of(2026, 10, 30)));
        assertEquals(List.of(YearMonth.of(2026, 10), YearMonth.of(2026, 11)),
            MonthlyInvoiceScheduler.periodsToEnsure(LocalDate.of(2026, 10, 31)));
        assertEquals(List.of(YearMonth.of(2026, 12), YearMonth.of(2027, 1)),
            MonthlyInvoiceScheduler.periodsToEnsure(LocalDate.of(2026, 12, 31)));
        assertEquals(List.of(YearMonth.of(2028, 2), YearMonth.of(2028, 3)),
            MonthlyInvoiceScheduler.periodsToEnsure(LocalDate.of(2028, 2, 29)));
    }

    @Test
    @DisplayName("On the last day, next month's invoice is created for ACTIVE contracts; running again creates nothing")
    void lastDayCreatesNextMonthOnce() {
        Contract active = contract(ContractStatus.ACTIVE, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 8, 1));
        Contract draft = contract(ContractStatus.DRAFT, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 8, 1));
        Contract ended = contract(ContractStatus.TERMINATED, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 8, 1));
        LocalDate lastDay = LocalDate.of(2026, 10, 31);

        // tháng hiện tại (10) và tháng sau (11) cho hợp đồng ACTIVE
        assertEquals(2, scheduler.run(lastDay));
        assertEquals(2, rentInvoices(active).size());
        assertTrue(rentInvoices(draft).isEmpty());
        assertTrue(rentInvoices(ended).isEmpty());
        assertTrue(rentInvoices(active).stream().anyMatch(i -> i.getMonth() == 11 && i.getYear() == 2026
            && i.getStatus() == InvoiceStatus.ISSUED
            && i.getDueDate().equals(LocalDate.of(2026, 11, 5))));

        assertEquals(0, scheduler.run(lastDay), "idempotent: nothing new on a second run");
        assertEquals(0, scheduler.run(LocalDate.of(2026, 10, 31)));
        assertEquals(2, rentInvoices(active).size());
    }

    @Test
    @DisplayName("Mid-month run only backfills a missing current-month invoice, never next month")
    void midMonthBackfillOnly() {
        Contract active = contract(ContractStatus.ACTIVE, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 8, 1));
        assertEquals(1, scheduler.run(LocalDate.of(2026, 10, 15)));
        assertEquals(1, rentInvoices(active).size());
        assertEquals(10, rentInvoices(active).get(0).getMonth());
    }

    @Test
    @DisplayName("The first month of a contract and months beyond its term are not auto-issued")
    void firstMonthAndTermBoundaries() {
        Contract startsThisMonth = contract(ContractStatus.ACTIVE, LocalDate.of(2026, 10, 10), LocalDate.of(2027, 10, 10));
        Contract endsThisMonth = contract(ContractStatus.ACTIVE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 20));

        scheduler.run(LocalDate.of(2026, 10, 31));

        assertTrue(rentInvoices(startsThisMonth).stream().noneMatch(i -> i.getMonth() == 10),
            "first month is left to the manager");
        assertEquals(1, rentInvoices(startsThisMonth).size(), "second month is created");
        assertEquals(1, rentInvoices(endsThisMonth).size(), "last month of the term is created");
        assertTrue(rentInvoices(endsThisMonth).stream().noneMatch(i -> i.getMonth() == 11), "nothing past the term");
    }

    @Test
    @DisplayName("Unpaid repair fees are rolled into the auto-created invoice, as itemized lines")
    void autoInvoiceRollsFees() {
        Contract c = contract(ContractStatus.ACTIVE, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 8, 1));
        scheduler.run(LocalDate.of(2026, 10, 1)); // hóa đơn tháng 10 đã có từ đầu tháng
        MaintenanceTicket ticket = ticketRepo.save(MaintenanceTicket.create(
            c.getRoomId(), c.getPropertyId(), c.getPrimaryTenantId(), "Vỡ kính", "Mô tả",
            MaintenancePriority.MEDIUM, MaintenanceCause.TENANT_USAGE));
        Invoice fee = Invoice.issueMaintenanceFee(c, ticket.getId(), new BigDecimal("450000"), "PHAAAA2222", LocalDate.of(2026, 10, 20));
        invoiceRepo.save(fee);

        scheduler.run(LocalDate.of(2026, 10, 31));

        Invoice november = rentInvoices(c).stream().filter(i -> i.getMonth() == 11).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("3950000").compareTo(november.getTotalAmount()));
        assertEquals(2, november.getItems().size());
        assertTrue(november.getItems().stream().anyMatch(i -> i.name().equals("Phí sửa chữa: Vỡ kính")));
        assertTrue(invoiceRepo.findById(fee.getId()).orElseThrow().isConsolidated());
        // khoản phí chỉ gộp vào đúng một hóa đơn (tháng 11), không phải hóa đơn tháng 10 đã có
        long withFee = rentInvoices(c).stream()
            .filter(i -> i.getItems().stream().anyMatch(item -> fee.getId().equals(item.sourceInvoiceId()))).count();
        assertEquals(1, withFee);
    }

    @Test
    @DisplayName("A failing contract is logged and does not stop the others")
    void failureIsolation() {
        Contract good = contract(ContractStatus.ACTIVE, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 8, 1));
        Contract bad = contract(ContractStatus.ACTIVE, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 8, 1));
        MonthlyInvoiceUseCase flaky = new MonthlyInvoiceUseCase() {
            @Override
            public List<UUID> findContractsNeedingInvoice(YearMonth period) {
                return service.findContractsNeedingInvoice(period);
            }

            @Override
            public boolean issueForContract(UUID contractId, YearMonth period) {
                if (contractId.equals(bad.getId())) {
                    throw new IllegalStateException("boom");
                }
                return service.issueForContract(contractId, period);
            }
        };

        int created = new MonthlyInvoiceScheduler(flaky).run(LocalDate.of(2026, 10, 15));

        assertEquals(1, created);
        assertEquals(1, rentInvoices(good).size());
        assertFalse(rentInvoices(bad).size() > 0);
    }
}
