package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresContractRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresInvoiceRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresPaymentClaimRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.PaymentClaim;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** V57 (#205): yêu cầu thanh toán được lưu, xác nhận/từ chối, và mỗi hóa đơn chỉ có một yêu cầu đang chờ. */
@Testcontainers(disabledWithoutDocker = true)
class PaymentClaimPostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("claims round-trip, the decision is stored and a second pending claim for the invoice is rejected by the DB")
    void claimsPersist() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker").load().migrate();

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        PostgresInvoiceRepository invoices = new PostgresInvoiceRepository(jdbc);
        Contract contract = new PostgresContractRepository(jdbc).findById(UUID.fromString(
            jdbc.getJdbcTemplate().queryForObject("SELECT id::text FROM contracts WHERE status = 'ACTIVE' LIMIT 1",
                String.class))).orElseThrow();
        Invoice invoice = invoices.save(Invoice.issueMonthlyRent(contract,
            YearMonth.from(contract.getStartDate()).plusMonths(5), invoices.newUniquePaymentCode()));
        UUID invoiceId = invoice.getId();
        UUID contractId = contract.getId();
        UUID propertyId = contract.getPropertyId();
        UUID userId = UUID.fromString(jdbc.getJdbcTemplate().queryForObject("SELECT id::text FROM users LIMIT 1",
            String.class));
        PostgresPaymentClaimRepository repo = new PostgresPaymentClaimRepository(jdbc);

        PaymentClaim pending = new PaymentClaim(UUID.randomUUID(), invoiceId, contractId, propertyId,
            new BigDecimal("500000"), PaymentClaim.Method.CASH, "Đã nộp", PaymentClaim.Source.TENANT_CLAIM,
            PaymentClaim.Status.PENDING, userId, java.time.Instant.now(), null, null, null);
        repo.save(pending);
        assertEquals(1, repo.findPendingByPropertyId(propertyId).size());
        assertTrue(repo.findByIdForUpdate(pending.id()).isPresent());

        PaymentClaim second = new PaymentClaim(UUID.randomUUID(), invoiceId, contractId, propertyId,
            new BigDecimal("100000"), PaymentClaim.Method.CASH, null, PaymentClaim.Source.TENANT_CLAIM,
            PaymentClaim.Status.PENDING, userId, java.time.Instant.now(), null, null, null);
        assertThrows(DuplicateKeyException.class, () -> repo.save(second));

        repo.save(pending.rejected(userId, "Chưa nhận được tiền"));
        PaymentClaim stored = repo.findById(pending.id()).orElseThrow();
        assertEquals(PaymentClaim.Status.REJECTED, stored.status());
        assertEquals("Chưa nhận được tiền", stored.rejectReason());
        assertEquals(0, repo.findPendingByPropertyId(propertyId).size());

        repo.save(second);
        repo.save(second.confirmed(userId));
        assertEquals(2, repo.findByInvoiceId(invoiceId).size());
        assertEquals(PaymentClaim.Status.CONFIRMED, repo.findById(second.id()).orElseThrow().status());
        assertEquals(2, repo.findByContractId(contractId).size());
    }
}
