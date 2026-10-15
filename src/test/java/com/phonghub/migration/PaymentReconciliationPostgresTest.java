package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresPaymentRepository;
import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.domain.model.PaymentReviewReason;
import com.phonghub.domain.model.PaymentStatus;
import com.phonghub.domain.model.PaymentTransaction;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #82: giao dịch theo danh sách hợp đồng và theo hóa đơn trên PostgreSQL, nội dung cá nhân được giải mã. */
@Testcontainers(disabledWithoutDocker = true)
class PaymentReconciliationPostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("findByContractIds lọc đúng hợp đồng, mới nhất trước; findByInvoiceId cũ nhất trước")
    void queries() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker").load().migrate();

        UUID contractId;
        UUID invoiceId = UUID.randomUUID();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT id, room_id, primary_tenant_id FROM contracts LIMIT 1");
            rs.next();
            contractId = UUID.fromString(rs.getString(1));
            st.execute("INSERT INTO invoices (id, contract_id, room_id, tenant_id, month, year, total_amount, due_date, status) "
                + "VALUES ('" + invoiceId + "', '" + contractId + "', '" + rs.getString(2) + "', '" + rs.getString(3)
                + "', 1, 2032, 3000000, '2032-01-05', 'ISSUED')");
        }

        PostgresPaymentRepository payments = new PostgresPaymentRepository(new NamedParameterJdbcTemplate(ds),
            new AesGcmTenantDataProtector("test-v1", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="));
        payments.saveIfNew(tx(920001, contractId, invoiceId, "2026-09-01T00:00:00Z", "1000000", null));
        payments.saveIfNew(tx(920002, contractId, invoiceId, "2026-09-03T00:00:00Z", "2000000", null));
        payments.saveIfNew(tx(920003, contractId, null, "2026-09-02T00:00:00Z", "0", PaymentReviewReason.UNMATCHED));
        payments.saveIfNew(tx(920004, null, null, "2026-09-04T00:00:00Z", "0", PaymentReviewReason.UNMATCHED));

        List<PaymentTransaction> byContract = payments.findByContractIds(Set.of(contractId));
        List<Long> ours = byContract.stream().map(PaymentTransaction::sepayId)
            .filter(id -> id >= 920001 && id <= 920004).toList();
        assertEquals(List.of(920002L, 920003L, 920001L), ours, "mới nhất trước, không lẫn giao dịch không có hợp đồng");
        assertEquals("Thanh toan 920001", byContract.stream().filter(t -> t.sepayId() == 920001).findFirst()
            .orElseThrow().content(), "nội dung chuyển khoản được giải mã");
        assertTrue(payments.findByContractIds(Set.of()).isEmpty());
        assertTrue(payments.findByContractIds(Set.of(UUID.randomUUID())).isEmpty());

        assertEquals(List.of(920001L, 920002L),
            payments.findByInvoiceId(invoiceId).stream().map(PaymentTransaction::sepayId).toList());
    }

    private static PaymentTransaction tx(long sepayId, UUID contractId, UUID invoiceId, String at, String applied,
                                         PaymentReviewReason reason) {
        return new PaymentTransaction(UUID.randomUUID(), sepayId, "MBBank", at, "0389999999", null, "in",
            new BigDecimal("1000000"), null, null, "Thanh toan " + sepayId, "REF" + sepayId, null, PaymentStatus.SUCCESS,
            contractId, invoiceId, Instant.parse(at), new BigDecimal(applied), BigDecimal.ZERO, reason);
    }
}
