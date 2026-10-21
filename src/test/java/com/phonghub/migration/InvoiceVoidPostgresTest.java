package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresContractRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresInvoiceRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import java.time.YearMonth;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #89 trên Postgres thật: hóa đơn tiền thuê bị hủy (VOIDED) giải phóng kỳ đó để phát hành lại. */
@Testcontainers(disabledWithoutDocker = true)
class InvoiceVoidPostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("Hai hóa đơn còn hiệu lực cùng kỳ bị chặn; hủy bản cũ thì phát hành lại được")
    void voidFreesThePeriod() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .load().migrate();
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        PostgresInvoiceRepository invoices = new PostgresInvoiceRepository(jdbc);
        Contract contract = new PostgresContractRepository(jdbc).findById(UUID.fromString(
            jdbc.getJdbcTemplate().queryForObject("SELECT id::text FROM contracts WHERE status = 'ACTIVE' LIMIT 1",
                String.class))).orElseThrow();
        YearMonth period = YearMonth.from(contract.getStartDate()).plusMonths(5);

        Invoice first = invoices.save(Invoice.issueMonthlyRent(contract, period, invoices.newUniquePaymentCode()));
        assertThrows(DataIntegrityViolationException.class, () -> invoices.save(
            Invoice.issueMonthlyRent(contract, period, invoices.newUniquePaymentCode())), "kỳ đang có hóa đơn hiệu lực");

        Invoice loaded = invoices.findByIdForUpdate(first.getId()).orElseThrow();
        loaded.voidInvoice();
        invoices.save(loaded);
        assertEquals(InvoiceStatus.VOIDED, invoices.findById(first.getId()).orElseThrow().getStatus());
        assertTrue(invoices.findActiveRentByContractIdAndPeriod(contract.getId(), period.getYear(),
            period.getMonthValue()).isEmpty());

        Invoice reissued = invoices.save(Invoice.issueMonthlyRent(contract, period, invoices.newUniquePaymentCode()));
        assertEquals(reissued.getId(), invoices.findActiveRentByContractIdAndPeriod(contract.getId(), period.getYear(),
            period.getMonthValue()).orElseThrow().getId());
    }
}
