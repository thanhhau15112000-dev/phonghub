package com.phonghub.migration;

import com.phonghub.adapter.out.persistence.postgres.PostgresInvoiceRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresMeterReadingRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresServiceRateRepository;
import com.phonghub.domain.model.ChargeType;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceItem;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.ServiceRate;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
class InvoiceUtilityChargesPostgresTest {

    private static final String MAIN = "classpath:db/migration";
    private static final String FIXTURE = "classpath:db/seed/docker";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private String firstValue(Statement st, String sql) throws Exception {
        ResultSet rs = st.executeQuery(sql);
        rs.next();
        return rs.getString(1);
    }

    @Test
    @DisplayName("V21: utility lines are persisted with their reading and rate, the invoice totals follow, and a line cannot repeat")
    void utilityLinesPersist() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).load().migrate();

        UUID contractId;
        UUID roomId;
        UUID tenantId;
        UUID userId;
        UUID propertyId;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            contractId = UUID.fromString(firstValue(st, "SELECT id FROM contracts LIMIT 1"));
            roomId = UUID.fromString(firstValue(st, "SELECT id FROM rooms LIMIT 1"));
            tenantId = UUID.fromString(firstValue(st, "SELECT id FROM tenants LIMIT 1"));
            userId = UUID.fromString(firstValue(st, "SELECT id FROM users LIMIT 1"));
            propertyId = UUID.fromString(firstValue(st, "SELECT property_id FROM rooms LIMIT 1"));
        }

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        PostgresInvoiceRepository invoices = new PostgresInvoiceRepository(jdbc);
        ServiceRate rate = new PostgresServiceRateRepository(jdbc).save(new ServiceRate(UUID.randomUUID(), propertyId,
            "Điện", ChargeType.PER_KWH, new BigDecimal("3500"), null, userId, null, userId));
        MeterReading reading = new PostgresMeterReadingRepository(jdbc).save(MeterReading.record(roomId, null,
            MeterType.ELECTRIC, LocalDate.of(2026, 10, 20), new BigDecimal("100"), new BigDecimal("150"), null,
            userId));

        BigDecimal rent = new BigDecimal("3500000");
        Invoice invoice = new Invoice(UUID.randomUUID(), contractId, roomId, tenantId, 10, 2026, rent, rent,
            BigDecimal.ZERO, LocalDate.of(2026, 10, 5), InvoiceStatus.ISSUED, "PHUTIL0001", InvoiceType.RENT, null,
            null, List.of(InvoiceItem.of("Tiền thuê phòng tháng 10/2026", rent, null)), null, null, null);
        invoices.save(invoice);

        invoice.addCharges(List.of(InvoiceItem.charge("Điện tháng 10/2026 (50 kWh)", new BigDecimal("50"),
            rate.pricePerUnit(), reading.id(), rate.id())));
        invoices.save(invoice);

        Invoice loaded = invoices.findById(invoice.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("3675000").compareTo(loaded.getTotalAmount()));
        assertEquals(2, loaded.getItems().size());
        assertEquals(0, new BigDecimal("175000").compareTo(loaded.utilityAmount()));
        assertEquals(true, loaded.hasCharge(reading.id(), rate.id()));

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            assertEquals("1", firstValue(st, "SELECT count(*) FROM invoices WHERE id = '" + invoice.getId()
                + "' AND utility_amount = 175000 AND other_amount = 0 AND total_amount = 3675000"));
            assertThrows(Exception.class, () -> st.execute("INSERT INTO invoice_items (id, invoice_id, item_name, "
                + "quantity, unit_price, total_amount, reading_id, rate_id) VALUES (gen_random_uuid(), '"
                + invoice.getId() + "', 'Trùng', 1, 1, 1, '" + reading.id() + "', '" + rate.id() + "')"),
                "the same reading and rate cannot be billed twice on one invoice");
        }
    }
}
