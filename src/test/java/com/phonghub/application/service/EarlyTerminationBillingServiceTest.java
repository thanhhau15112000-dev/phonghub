package com.phonghub.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryInvoiceRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryMeterReadingRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryServiceRateRepository;
import com.phonghub.domain.model.ChargeType;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
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

class EarlyTerminationBillingServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 25);
    private static final LocalDate END = LocalDate.of(2027, 10, 24);
    private InMemoryInvoiceRepository invoices;
    private InMemoryMeterReadingRepository readings;
    private InMemoryServiceRateRepository rates;
    private Contract contract;
    private EarlyTerminationBillingService service;

    @BeforeEach
    void setUp() {
        invoices = new InMemoryInvoiceRepository();
        readings = new InMemoryMeterReadingRepository();
        rates = new InMemoryServiceRateRepository();
        contract = new Contract(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("1000000"), new BigDecimal("3500000"), START, END, 5, ContractStatus.ACTIVE,
            List.of(), Instant.now(), Instant.now());
        service = new EarlyTerminationBillingService(invoices, readings, rates, event -> {});
    }

    private Invoice invoice(YearMonth period, String code) {
        return Invoice.issueMonthlyRent(contract, period, code);
    }

    @Test
    @DisplayName("Early termination halves current-cycle rent, includes unbilled meter use, and credits prepaid rent")
    void currentCycleAndFuturePrepaymentAreSettled() {
        Invoice current = invoice(YearMonth.of(2026, 10), "PHCURR0001");
        current.applyPayment(new BigDecimal("3500000"), Instant.now());
        invoices.save(current);

        Invoice futurePaid = invoice(YearMonth.of(2026, 11), "PHFUTR0002");
        futurePaid.applyPayment(new BigDecimal("3500000"), Instant.now());
        invoices.save(futurePaid);
        Invoice futureUnpaid = invoice(YearMonth.of(2026, 12), "PHFUTR0003");
        invoices.save(futureUnpaid);

        UUID electricRateId = UUID.randomUUID();
        rates.save(new ServiceRate(electricRateId, contract.getPropertyId(), "Điện", ChargeType.PER_KWH,
            new BigDecimal("2000"), Instant.now(), null, Instant.now(), null));
        MeterReading reading = MeterReading.record(contract.getRoomId(), contract.getId(), MeterType.ELECTRIC,
            LocalDate.of(2026, 10, 30), new BigDecimal("100"), new BigDecimal("110"), null, null);
        readings.save(reading);

        service.billEarlyTermination(contract, null, LocalDate.of(2026, 10, 30));

        Invoice cancellation = invoices.findById(current.getId()).orElseThrow();
        assertEquals(InvoiceType.CANCELLATION, cancellation.getType());
        assertEquals(0, new BigDecimal("1770000").compareTo(cancellation.getTotalAmount()));
        assertEquals(InvoiceStatus.PAID, cancellation.getStatus());
        assertEquals(0, new BigDecimal("1730000").compareTo(cancellation.getSettlementCreditAmount()));
        assertEquals(LocalDate.of(2026, 10, 25), cancellation.getCycleStartDate());
        assertEquals(1, cancellation.getCycleNumber());
        assertEquals(0, new BigDecimal("5230000").compareTo(contract.getSettlementCreditAmount()));
        Invoice refundedFuture = invoices.findById(futurePaid.getId()).orElseThrow();
        assertEquals(InvoiceStatus.VOIDED, refundedFuture.getStatus());
        assertEquals(0, new BigDecimal("3500000").compareTo(refundedFuture.getSettlementCreditAmount()));
        assertEquals(InvoiceStatus.VOIDED, invoices.findById(futureUnpaid.getId()).orElseThrow().getStatus());
        assertTrue(cancellation.getItems().stream().anyMatch(item -> item.isUtilityCharge()
            && item.readingId().equals(reading.id()) && item.rateId().equals(electricRateId)));
        assertEquals(0, new BigDecimal("1000000").compareTo(contract.availableDeposit()));
    }

    @Test
    @DisplayName("Insufficient deposit pays only what is available and leaves the rest due")
    void insufficientDepositLeavesBalanceOwed() {
        contract = new Contract(contract.getId(), contract.getPropertyId(), contract.getRoomId(), contract.getPrimaryTenantId(),
            new BigDecimal("500000"), contract.getRentAmount(), START, END, 5, ContractStatus.ACTIVE, List.of(),
            Instant.now(), Instant.now());
        service = new EarlyTerminationBillingService(invoices, null, null, event -> {});

        service.billEarlyTermination(contract, null, LocalDate.of(2026, 10, 30));

        Invoice cancellation = invoices.findByContractId(contract.getId()).stream().findFirst().orElseThrow();
        assertEquals(InvoiceType.CANCELLATION, cancellation.getType());
        assertEquals(0, new BigDecimal("1750000").compareTo(cancellation.getTotalAmount()));
        assertEquals(0, new BigDecimal("500000").compareTo(cancellation.getPaidAmount()));
        assertEquals(0, new BigDecimal("1250000").compareTo(cancellation.remainingAmount()));
        assertEquals(0, new BigDecimal("500000").compareTo(contract.getDepositAppliedAmount()));
        assertEquals(0, contract.availableDeposit().signum());
    }

    @Test
    @DisplayName("Termination on endDate does not create an early-cancellation charge")
    void endDateIsNotEarlyTermination() {
        service.billEarlyTermination(contract, null, END);

        assertTrue(invoices.findByContractId(contract.getId()).isEmpty());
        assertFalse(contract.getSettlementCreditAmount().signum() > 0);
        assertEquals(0, contract.getDepositAppliedAmount().signum());
    }
}
