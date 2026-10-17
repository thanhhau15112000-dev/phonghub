package com.phonghub.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.in.scheduling.MonthlyInvoiceScheduler;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMaintenanceTicketRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMeterReadingRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryServiceRateRepository;
import com.phonghub.domain.exception.DomainException;
import com.phonghub.domain.model.ChargeType;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceItem;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.ServiceRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UtilityInvoicingUnitTest {

    private static final YearMonth PERIOD = YearMonth.of(2026, 10);

    private InMemoryContractRepository contracts;
    private InMemoryInvoiceRepository invoices;
    private InMemoryMeterReadingRepository readings;
    private InMemoryServiceRateRepository rates;
    private MonthlyInvoiceService service;
    private UtilityBilling billing;
    private Contract contract;

    @BeforeEach
    void setUp() {
        contracts = new InMemoryContractRepository();
        invoices = new InMemoryInvoiceRepository();
        readings = new InMemoryMeterReadingRepository();
        rates = new InMemoryServiceRateRepository();
        service = new MonthlyInvoiceService(contracts, invoices, new InMemoryMaintenanceTicketRepository(),
            readings, rates);
        billing = new UtilityBilling(readings, rates);
        contract = new Contract(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("3000000"), new BigDecimal("3500000"), LocalDate.of(2026, 8, 1), LocalDate.of(2027, 8, 1), 5,
            ContractStatus.ACTIVE, List.of(), Instant.now(), Instant.now());
        contracts.save(contract);
    }

    private ServiceRate rate(String name, ChargeType type, String price) {
        return rates.save(new ServiceRate(UUID.randomUUID(), contract.getPropertyId(), name, type,
            new BigDecimal(price), null, null, null, null));
    }

    private MeterReading reading(MeterType type, String previous, String current, YearMonth period, UUID contractId) {
        return readings.save(MeterReading.record(contract.getRoomId(), contractId, type, period.atDay(20),
            new BigDecimal(previous), new BigDecimal(current), null, UUID.randomUUID()));
    }

    private Invoice issue() {
        assertTrue(service.issueForContract(contract.getId(), PERIOD));
        return invoices.findActiveRentByContractIdAndPeriod(contract.getId(), 2026, 10).orElseThrow();
    }

    @Test
    @DisplayName("Readings of the period become electricity and water lines; the total includes them")
    void issueWithReadings() {
        rate("Điện", ChargeType.PER_KWH, "3500");
        rate("Nước", ChargeType.PER_M3, "20000");
        reading(MeterType.ELECTRIC, "100", "180", PERIOD, contract.getId());
        reading(MeterType.WATER, "10", "15", PERIOD, contract.getId());

        Invoice invoice = issue();

        assertEquals(3, invoice.getItems().size());
        // 3.500.000 + 80 kWh x 3.500 + 5 m3 x 20.000
        assertEquals(0, new BigDecimal("3880000").compareTo(invoice.getTotalAmount()));
        assertEquals(0, new BigDecimal("380000").compareTo(invoice.utilityAmount()));
        assertTrue(billing.warnings(invoice, contract).isEmpty());
    }

    @Test
    @DisplayName("Missing readings never block the rent invoice; they only produce warnings")
    void missingReadingsWarn() {
        rate("Điện", ChargeType.PER_KWH, "3500");
        rate("Nước", ChargeType.PER_M3, "20000");

        Invoice invoice = issue();

        assertEquals(1, invoice.getItems().size());
        assertEquals(0, new BigDecimal("3500000").compareTo(invoice.getTotalAmount()));
        List<String> warnings = billing.warnings(invoice, contract);
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("điện"));
        assertTrue(warnings.get(1).contains("nước"));
    }

    @Test
    @DisplayName("A property without a water rate is not warned about water")
    void noRateNoNoise() {
        rate("Điện", ChargeType.PER_KWH, "3500");
        reading(MeterType.ELECTRIC, "0", "10", PERIOD, contract.getId());
        Invoice invoice = issue();
        assertTrue(billing.warnings(invoice, contract).isEmpty());
    }

    @Test
    @DisplayName("A reading with no configured rate warns that it is not billed")
    void readingWithoutRateWarns() {
        reading(MeterType.ELECTRIC, "0", "10", PERIOD, contract.getId());
        Invoice invoice = issue();
        assertEquals(1, invoice.getItems().size());
        assertEquals(1, billing.warnings(invoice, contract).size());
    }

    @Test
    @DisplayName("A reading recorded after issuing is added once by the scheduler pass")
    void lateReadingIsAttachedOnce() {
        rate("Điện", ChargeType.PER_KWH, "3500");
        Invoice issued = issue();
        assertTrue(service.findContractsNeedingUtilityCharges(PERIOD).isEmpty());

        reading(MeterType.ELECTRIC, "100", "150", PERIOD, contract.getId());
        assertEquals(List.of(contract.getId()), service.findContractsNeedingUtilityCharges(PERIOD));
        assertTrue(service.attachUtilityCharges(contract.getId(), PERIOD));

        Invoice updated = invoices.findById(issued.getId()).orElseThrow();
        assertEquals(2, updated.getItems().size());
        assertEquals(0, new BigDecimal("3675000").compareTo(updated.getTotalAmount()));
        assertFalse(service.attachUtilityCharges(contract.getId(), PERIOD));
        assertTrue(service.findContractsNeedingUtilityCharges(PERIOD).isEmpty());
        assertEquals(2, invoices.findById(issued.getId()).orElseThrow().getItems().size());
    }

    @Test
    @DisplayName("A legacy invoice without a payment code can still receive late utility charges")
    void lateReadingIsAttachedToLegacyInvoiceWithoutPaymentCode() {
        rate("Điện", ChargeType.PER_KWH, "3500");
        reading(MeterType.ELECTRIC, "100", "150", PERIOD, contract.getId());
        Instant now = Instant.now();
        Invoice legacyInvoice = new Invoice(
            UUID.randomUUID(), contract.getId(), contract.getRoomId(), contract.getPrimaryTenantId(),
            PERIOD.getMonthValue(), PERIOD.getYear(), contract.getRentAmount(), contract.getRentAmount(),
            BigDecimal.ZERO, PERIOD.atDay(5), InvoiceStatus.ISSUED, null, InvoiceType.RENT, null,
            null, List.of(InvoiceItem.of("Tiền thuê", contract.getRentAmount(), null)), null, now, now);
        invoices.save(legacyInvoice);

        assertTrue(service.attachUtilityCharges(contract.getId(), PERIOD));

        Invoice updated = invoices.findById(legacyInvoice.getId()).orElseThrow();
        assertEquals(2, updated.getItems().size());
        assertEquals(0, new BigDecimal("3675000").compareTo(updated.getTotalAmount()));
    }

    @Test
    @DisplayName("An invoice that already received money is never changed; the late reading is flagged")
    void paidInvoiceIsLeftAlone() {
        rate("Điện", ChargeType.PER_KWH, "3500");
        Invoice issued = issue();
        issued.applyPayment(new BigDecimal("1000000"), Instant.now());
        invoices.save(issued);

        reading(MeterType.ELECTRIC, "100", "150", PERIOD, contract.getId());

        assertTrue(service.findContractsNeedingUtilityCharges(PERIOD).isEmpty());
        assertFalse(service.attachUtilityCharges(contract.getId(), PERIOD));
        Invoice after = invoices.findById(issued.getId()).orElseThrow();
        assertEquals(1, after.getItems().size());
        assertEquals(0, new BigDecimal("3500000").compareTo(after.getTotalAmount()));
        List<String> warnings = billing.warnings(after, contract);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("thanh toán"));
        assertThrows(DomainException.class, () -> after.addCharges(
            billing.chargesFor(contract, PERIOD, false)));
    }

    @Test
    @DisplayName("Fixed services (per room, per person) are billed at issue only; a rate added later does not change it")
    void fixedServices() {
        UUID second = UUID.randomUUID();
        Contract withTwo = new Contract(contract.getId(), contract.getPropertyId(), contract.getRoomId(),
            contract.getPrimaryTenantId(), contract.getDepositAmount(), contract.getRentAmount(),
            contract.getStartDate(), contract.getEndDate(), 5, ContractStatus.ACTIVE,
            List.of(new ContractOccupant(UUID.randomUUID(), contract.getId(), contract.getPrimaryTenantId(), true,
                    LocalDate.of(2026, 8, 1), null),
                new ContractOccupant(UUID.randomUUID(), contract.getId(), second, false, LocalDate.of(2026, 8, 1), null),
                new ContractOccupant(UUID.randomUUID(), contract.getId(), UUID.randomUUID(), false,
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1))),
            Instant.now(), Instant.now());
        contracts.save(withTwo);
        rate("Rác", ChargeType.PER_ROOM_FLAT, "30000");
        rate("Wifi", ChargeType.PER_PERSON, "50000");

        Invoice invoice = issue();

        // 3.500.000 + 30.000 + 2 người (một người đã trả phòng) x 50.000
        assertEquals(0, new BigDecimal("3630000").compareTo(invoice.getTotalAmount()));

        rate("Giữ xe", ChargeType.PER_ROOM_FLAT, "100000");
        assertTrue(service.findContractsNeedingUtilityCharges(PERIOD).isEmpty());
        assertFalse(service.attachUtilityCharges(contract.getId(), PERIOD));
    }

    @Test
    @DisplayName("Zero consumption adds no line and no warning")
    void zeroConsumption() {
        rate("Điện", ChargeType.PER_KWH, "3500");
        reading(MeterType.ELECTRIC, "100", "100", PERIOD, contract.getId());
        Invoice invoice = issue();
        assertEquals(1, invoice.getItems().size());
        assertTrue(billing.warnings(invoice, contract).isEmpty());
    }

    @Test
    @DisplayName("Readings of another period or of a previous tenant's contract are not billed")
    void otherPeriodOrContractIgnored() {
        rate("Điện", ChargeType.PER_KWH, "3500");
        reading(MeterType.ELECTRIC, "0", "50", PERIOD.minusMonths(1), contract.getId());
        Invoice invoice = issue();
        assertEquals(1, invoice.getItems().size());

        readings.clear();
        reading(MeterType.ELECTRIC, "0", "50", PERIOD, UUID.randomUUID());
        assertEquals(1, invoices.findById(invoice.getId()).orElseThrow().getItems().size());
        assertTrue(service.findContractsNeedingUtilityCharges(PERIOD).isEmpty());
    }

    @Test
    @DisplayName("A meter replacement bills the combined consumption; amounts round to whole dong")
    void replacementAndRounding() {
        rate("Điện", ChargeType.PER_KWH, "3333");
        readings.save(MeterReading.record(contract.getRoomId(), contract.getId(), MeterType.ELECTRIC,
            PERIOD.atDay(20), new BigDecimal("990"), new BigDecimal("12.5"),
            new MeterReading.ReplacementInfo(new BigDecimal("1000"), new BigDecimal("0"), "Thay đồng hồ"),
            UUID.randomUUID()));
        Invoice invoice = issue();
        // (1000 - 990) + 12.5 = 22.5 kWh x 3.333 = 74.992,5 -> 74.993
        assertEquals(0, new BigDecimal("3574993").compareTo(invoice.getTotalAmount()));
    }

    @Test
    @DisplayName("The scheduler pass attaches late readings for the current and previous month")
    void schedulerAttachesLateReadings() {
        rate("Điện", ChargeType.PER_KWH, "3500");
        Invoice issued = issue();
        reading(MeterType.ELECTRIC, "0", "10", PERIOD, contract.getId());

        MonthlyInvoiceScheduler scheduler = new MonthlyInvoiceScheduler(service);
        assertEquals(List.of(PERIOD, PERIOD.minusMonths(1)),
            MonthlyInvoiceScheduler.utilityPeriods(LocalDate.of(2026, 10, 12)));
        assertEquals(1, scheduler.attachUtilityCharges(LocalDate.of(2026, 10, 12)));
        assertEquals(0, scheduler.attachUtilityCharges(LocalDate.of(2026, 10, 12)));
        assertEquals(2, invoices.findById(issued.getId()).orElseThrow().getItems().size());
    }
}
