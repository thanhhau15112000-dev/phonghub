package com.phonghub.migration;

import com.phonghub.adapter.out.persistence.postgres.PostgresContractRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresInvoiceRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresLateFeePolicyRepository;
import com.phonghub.application.service.LateFeeAssessmentService;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceItem;
import com.phonghub.domain.model.LateFeePolicy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** #80/#225: chính sách phạt lưu được, tiền phạt tăng theo ngày trong mức trần, hóa đơn quá hạn được tìm đúng. */
@Testcontainers(disabledWithoutDocker = true)
class LateFeePostgresTest {

    private static final String MAIN = "classpath:db/migration";
    private static final String FIXTURE = "classpath:db/seed/docker";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private PGSimpleDataSource migrated() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).load().migrate();
        return ds;
    }

    private String scalar(Statement st, String sql) throws SQLException {
        ResultSet rs = st.executeQuery(sql);
        rs.next();
        return rs.getString(1);
    }

    @Test
    @DisplayName("policy round-trip, DB constraints, and a capped daily assessment on a real overdue invoice")
    void policyAndAssessment() throws Exception {
        PGSimpleDataSource ds = migrated();
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        PostgresContractRepository contracts = new PostgresContractRepository(jdbc);
        PostgresInvoiceRepository invoices = new PostgresInvoiceRepository(jdbc);
        PostgresLateFeePolicyRepository policies = new PostgresLateFeePolicyRepository(jdbc);

        UUID contractId;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            contractId = UUID.fromString(scalar(st, "SELECT id FROM contracts LIMIT 1"));
        }
        Contract contract = contracts.findById(contractId).orElseThrow();
        assumeTrue(contract.isActive(), "seed contract is not active");
        UUID propertyId = contract.getPropertyId();

        assertTrue(policies.findByPropertyId(propertyId).isEmpty());
        LateFeePolicy saved = policies.save(new LateFeePolicy(propertyId, true, LateFeePolicy.FeeType.PERCENT,
            new BigDecimal("2.5"), 2, new BigDecimal("100000"), Instant.now(), null));
        LateFeePolicy loaded = policies.findByPropertyId(propertyId).orElseThrow();
        assertEquals(saved.feeType(), loaded.feeType());
        assertEquals(0, new BigDecimal("2.5").compareTo(loaded.feeValue()));
        assertEquals(2, loaded.graceDays());
        assertEquals(0, new BigDecimal("100000").compareTo(loaded.capAmount()));

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            assertThrows(SQLException.class, () -> st.execute("UPDATE property_late_fee_policies SET fee_value = 150 "
                + "WHERE property_id = '" + propertyId + "'"), "percent above 100 is rejected by the DB");
        }

        YearMonth period = YearMonth.from(contract.getStartDate());
        Invoice created = invoices.save(Invoice.issueMonthlyRent(contract, period, "PHLATEPG01"));
        LocalDate today = created.getDueDate().plusDays(10);
        assertTrue(invoices.findOverdueRentInvoiceIds(today).contains(created.getId()));
        assertFalse(invoices.findOverdueRentInvoiceIds(created.getDueDate()).contains(created.getId()),
            "not overdue on the due date");

        LateFeeAssessmentService service = new LateFeeAssessmentService(invoices, contracts, policies, event -> { });
        BigDecimal principal = created.getTotalAmount();
        assertTrue(service.assessInvoice(created.getId(), today));
        assertFalse(service.assessInvoice(created.getId(), today));
        assertTrue(service.assessInvoice(created.getId(), today.plusDays(20)),
            "same cap amount still updates the displayed number of penalty days");

        Invoice reloaded = invoices.findById(created.getId()).orElseThrow();
        BigDecimal expectedFee = policies.findByPropertyId(propertyId).orElseThrow().feeFor(principal, 10);
        assertEquals(1, reloaded.getItems().stream().filter(InvoiceItem::isLateFee).count());
        assertEquals(0, principal.add(expectedFee).compareTo(reloaded.getTotalAmount()));
        assertEquals(0, principal.compareTo(reloaded.principalAmount()));
        assertEquals("Phạt trả trễ (28 ngày)", reloaded.lateFeeItem().orElseThrow().name());
        assertTrue(invoices.findByIdForUpdate(created.getId()).isPresent());
    }
}
