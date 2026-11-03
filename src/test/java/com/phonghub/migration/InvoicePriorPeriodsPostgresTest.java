package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresInvoiceRepository;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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

@Testcontainers(disabledWithoutDocker = true)
class InvoicePriorPeriodsPostgresTest {
    private static final String MAIN = "classpath:db/migration";
    private static final String FIXTURE = "classpath:db/seed/docker";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("Same-room history returns the latest three distinct prior rent periods and omits drafts and voids")
    void findsPriorDistinctRentPeriods() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations(MAIN, FIXTURE).load().migrate();

        UUID contractId;
        UUID propertyId;
        UUID roomId;
        UUID tenantId;
        UUID anotherRoomId;
        UUID anotherRoomPropertyId;
        UUID ticketId;
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet contract = statement.executeQuery(
                "SELECT id, property_id, room_id, primary_tenant_id FROM contracts LIMIT 1")) {
                assertTrue(contract.next());
                contractId = contract.getObject("id", UUID.class);
                propertyId = contract.getObject("property_id", UUID.class);
                roomId = contract.getObject("room_id", UUID.class);
                tenantId = contract.getObject("primary_tenant_id", UUID.class);
            }
            try (ResultSet room = statement.executeQuery(
                "SELECT id, property_id FROM rooms WHERE id <> '" + roomId + "' LIMIT 1")) {
                assertTrue(room.next());
                anotherRoomId = room.getObject("id", UUID.class);
                anotherRoomPropertyId = room.getObject("property_id", UUID.class);
            }
            try (ResultSet ticket = statement.executeQuery("SELECT id FROM maintenance_tickets LIMIT 1")) {
                assertTrue(ticket.next());
                ticketId = ticket.getObject("id", UUID.class);
            }
            UUID testContractId = UUID.randomUUID();
            statement.execute("""
                INSERT INTO contracts (id, property_id, room_id, primary_tenant_id, deposit_amount, rent_amount,
                                       start_date, end_date, payment_day, status)
                VALUES ('%s', '%s', '%s', '%s', 0, 1000, '2026-01-01', '2026-12-31', 5, 'TERMINATED')
                """.formatted(testContractId, propertyId, roomId, tenantId));
            UUID secondContractId = UUID.randomUUID();
            statement.execute("""
                INSERT INTO contracts (id, property_id, room_id, primary_tenant_id, deposit_amount, rent_amount,
                                       start_date, end_date, payment_day, status)
                VALUES ('%s', '%s', '%s', '%s', 0, 1000, '2026-01-01', '2026-12-31', 5, 'TERMINATED')
                """.formatted(secondContractId, propertyId, roomId, tenantId));
            UUID anotherRoomContractId = UUID.randomUUID();
            statement.execute("""
                INSERT INTO contracts (id, property_id, room_id, primary_tenant_id, deposit_amount, rent_amount,
                                       start_date, end_date, payment_day, status)
                VALUES ('%s', '%s', '%s', '%s', 0, 1000, '2026-01-01', '2026-12-31', 5, 'TERMINATED')
                """.formatted(anotherRoomContractId, anotherRoomPropertyId, anotherRoomId, tenantId));

            NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
            PostgresInvoiceRepository invoices = new PostgresInvoiceRepository(jdbc);
            Invoice septemberOlder = save(invoices, roomId, testContractId, tenantId, 9, InvoiceStatus.ISSUED,
                InvoiceType.RENT, "2026-10-01T00:00:00Z");
            Invoice septemberNewer = save(invoices, roomId, secondContractId, tenantId, 9, InvoiceStatus.ISSUED,
                InvoiceType.RENT, "2026-10-02T00:00:00Z");
            save(invoices, roomId, testContractId, tenantId, 8, InvoiceStatus.DRAFT, InvoiceType.RENT,
                "2026-09-01T00:00:00Z");
            save(invoices, roomId, testContractId, tenantId, 8, InvoiceStatus.ISSUED, InvoiceType.MAINTENANCE,
                ticketId, "2026-09-02T00:00:00Z");
            save(invoices, roomId, testContractId, tenantId, 7, InvoiceStatus.ISSUED, InvoiceType.RENT,
                "2026-08-01T00:00:00Z");
            save(invoices, roomId, testContractId, tenantId, 6, InvoiceStatus.ISSUED, InvoiceType.RENT,
                "2026-07-01T00:00:00Z");
            save(invoices, roomId, testContractId, tenantId, 5, InvoiceStatus.VOIDED, InvoiceType.RENT,
                "2026-06-01T00:00:00Z");
            save(invoices, anotherRoomId, anotherRoomContractId, tenantId, 9, InvoiceStatus.ISSUED, InvoiceType.RENT,
                "2026-10-03T00:00:00Z");

            List<Invoice> periods = invoices.findPriorRentByRoomId(roomId, YearMonth.of(2026, 10), 3);

            assertEquals(3, periods.size());
            assertEquals(List.of(9, 7, 6), periods.stream().map(Invoice::getMonth).toList());
            assertEquals(septemberNewer.getId(), periods.getFirst().getId());
            assertTrue(!periods.getFirst().getId().equals(septemberOlder.getId()));
        }
    }

    private static Invoice save(PostgresInvoiceRepository invoices, UUID roomId, UUID contractId, UUID tenantId,
                                int month, InvoiceStatus status, InvoiceType type, String createdAt) {
        return save(invoices, roomId, contractId, tenantId, month, status, type, null, createdAt);
    }

    private static Invoice save(PostgresInvoiceRepository invoices, UUID roomId, UUID contractId, UUID tenantId,
                                int month, InvoiceStatus status, InvoiceType type, UUID ticketId, String createdAt) {
        Instant at = Instant.parse(createdAt);
        Invoice invoice = new Invoice(UUID.randomUUID(), contractId, roomId, tenantId, month, 2026,
            new BigDecimal("1000"), new BigDecimal("1000"), BigDecimal.ZERO, LocalDate.of(2026, month, 28),
            status, "PH" + UUID.randomUUID().toString().replace("-", "").substring(0, 18),
            type, ticketId, null, at, at);
        return invoices.save(invoice);
    }
}
