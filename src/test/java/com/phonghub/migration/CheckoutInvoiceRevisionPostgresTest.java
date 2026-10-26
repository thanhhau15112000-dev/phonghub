package com.phonghub.migration;

import com.phonghub.adapter.out.persistence.postgres.PostgresContractRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresInvoiceRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresServiceRateRepository;
import com.phonghub.domain.model.ChargeType;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceItem;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.ServiceRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class CheckoutInvoiceRevisionPostgresTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void clearingAndRestoringCheckoutChargesRoundTripsReceiptsAndCredits() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker").load().migrate();
        var jdbc = new NamedParameterJdbcTemplate(ds);
        var invoices = new PostgresInvoiceRepository(jdbc);
        var contracts = new PostgresContractRepository(jdbc);
        UUID contractId = jdbc.getJdbcTemplate().queryForObject("SELECT id FROM contracts WHERE status = 'ACTIVE' LIMIT 1", UUID.class);
        var contract = contracts.findById(contractId).orElseThrow();
        UUID rateId = UUID.randomUUID();
        new PostgresServiceRateRepository(jdbc).save(new ServiceRate(rateId, contract.getPropertyId(), "Revision electricity",
            ChargeType.PER_KWH, new BigDecimal("3000"), Instant.now(), null, null, null));
        var charge = InvoiceItem.checkOutCharge("Điện", BigDecimal.TEN, new BigDecimal("3000"), "kWh", rateId);
        var invoice = Invoice.issueCheckOutUtilities(contract, YearMonth.now(), invoices.newUniquePaymentCode(),
            LocalDate.now().plusDays(3), List.of(charge));
        invoice.applyPayment(new BigDecimal("30000"), Instant.now());
        invoices.save(invoice);

        invoice = invoices.findById(invoice.getId()).orElseThrow();
        invoice.reviseCheckOutUtilities(List.of());
        contract.reviseSettlementCredit(BigDecimal.ZERO, invoice.getSettlementCreditAmount());
        contracts.save(contract);
        invoices.save(invoice);
        var cleared = invoices.findById(invoice.getId()).orElseThrow();
        assertTrue(cleared.getItems().isEmpty(), "old invoice_items must be deleted even when all charges disappear");
        assertEquals(0, cleared.getTotalAmount().signum());
        assertEquals(InvoiceStatus.PAID, cleared.getStatus());
        assertEquals(0, new BigDecimal("30000").compareTo(cleared.getSettlementCreditAmount()));
        assertEquals(0, new BigDecimal("30000").compareTo(contracts.findById(contractId).orElseThrow().getSettlementCreditAmount()));

        cleared.reviseCheckOutUtilities(List.of(charge));
        contract.reviseSettlementCredit(new BigDecimal("30000"), cleared.getSettlementCreditAmount());
        contracts.save(contract);
        invoices.save(cleared);
        var restored = invoices.findById(invoice.getId()).orElseThrow();
        assertEquals(1, restored.getItems().size());
        assertEquals(0, new BigDecimal("30000").compareTo(restored.getPaidAmount()));
        assertEquals(0, restored.getSettlementCreditAmount().signum());
        assertEquals(0, contracts.findById(contractId).orElseThrow().getSettlementCreditAmount().signum());
    }
}
