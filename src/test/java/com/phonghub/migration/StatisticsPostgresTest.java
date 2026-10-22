package com.phonghub.migration;

import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresInvoiceRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresStatisticsQueryAdapter;
import com.phonghub.adapter.out.persistence.postgres.PostgresUserRepository;
import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.application.port.out.StatisticsQueryPort.ActivityCount;
import com.phonghub.application.port.out.StatisticsQueryPort.InvoiceAggregate;
import com.phonghub.application.port.out.StatisticsQueryPort.MaintenanceAggregate;
import com.phonghub.application.port.out.StatisticsQueryPort.OpenMaintenanceCount;
import com.phonghub.application.port.out.StatisticsQueryPort.PendingPropertyRow;
import com.phonghub.application.port.out.StatisticsQueryPort.SignupCount;
import com.phonghub.application.port.out.TenantDataProtectionPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #93: các truy vấn GROUP BY thống kê chạy đúng trên PostgreSQL thật và có index V39. */
@Testcontainers(disabledWithoutDocker = true)
class StatisticsPostgresTest {

    private static final String MAIN = "classpath:db/migration";
    private static final String FIXTURE = "classpath:db/seed/docker";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static String scalar(Statement st, String sql) throws Exception {
        ResultSet rs = st.executeQuery(sql);
        rs.next();
        return rs.getString(1);
    }

    @Test
    @DisplayName("hóa đơn, phòng, bảo trì, nhà chờ duyệt, tài khoản và hoạt động được tổng hợp đúng")
    void aggregates() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).load().migrate();

        UUID tenantId;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            tenantId = UUID.fromString(scalar(st, "SELECT id FROM tenants LIMIT 1"));
            for (String index : List.of("idx_contracts_property", "idx_invoices_period_active",
                "idx_maintenance_events_done", "idx_maintenance_tickets_open", "idx_users_created_at",
                "idx_audit_logs_created_at")) {
                assertEquals("1", scalar(st, "SELECT COUNT(*) FROM pg_indexes WHERE indexname = '" + index + "'"), index);
            }
        }

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);

        JpaTestContext jpa = JpaTestContext.over(ds);
        TenantDataProtectionPort protector = new AesGcmTenantDataProtector("test-v1",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=");
        PostgresStatisticsQueryAdapter stats = new PostgresStatisticsQueryAdapter(jdbc, protector);

        UUID ownerId = UUID.randomUUID();
        new PostgresUserRepository(jdbc, protector).save(new User(ownerId, "stats.owner", "stats.owner@example.com",
            "Chủ Thống Kê", "0999888777", UserRole.OWNER, User.UserStatus.ACTIVE, false, Instant.parse("2030-03-10T00:00:00Z")));

        PropertyRepositoryPort properties = jpa.properties();
        UUID p = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        properties.save(new Property(p, "Nhà thống kê", "addr", null, 3, ownerId, PropertyApprovalStatus.VERIFIED, null,
            Instant.now()));
        properties.save(new Property(pending, "Nhà chờ duyệt thống kê", "addr 2", null, 1, ownerId,
            PropertyApprovalStatus.PENDING, null, Instant.now()));

        RoomRepositoryPort rooms = jpa.rooms();
        Room r1 = rooms.save(room(p, "S1", RoomStatus.OCCUPIED));
        Room r2 = rooms.save(room(p, "S2", RoomStatus.MAINTENANCE));
        Room r3 = rooms.save(room(p, "S3", RoomStatus.AVAILABLE));
        Contract contract = jpa.contracts().save(new Contract(UUID.randomUUID(), p, r1.getId(),
            tenantId, new BigDecimal("1000000"), new BigDecimal("3000000"), LocalDate.of(2026, 1, 1),
            LocalDate.of(2027, 1, 1), 5, ContractStatus.ACTIVE, List.of(), Instant.now(), Instant.now()));

        PostgresInvoiceRepository invoices = new PostgresInvoiceRepository(jdbc);
        invoices.save(invoice(contract, 2026, 8, "3000000", "3000000", InvoiceStatus.PAID, LocalDate.of(2026, 8, 5)));
        invoices.save(invoice(contract, 2026, 9, "3200000", "1000000", InvoiceStatus.PARTIALLY_PAID, LocalDate.of(2026, 9, 5)));
        Invoice october = invoices.save(invoice(contract, 2026, 10, "3000000", "0", InvoiceStatus.ISSUED,
            LocalDate.of(2026, 10, 20)));
        invoices.save(invoice(contract, 2026, 7, "9999", "0", InvoiceStatus.VOIDED, LocalDate.of(2026, 7, 5)));
        invoices.save(invoice(contract, 2025, 1, "1000000", "0", InvoiceStatus.ISSUED, LocalDate.of(2025, 1, 5)));

        // Phiếu bảo trì và mốc thời gian cố định để kiểm tra giờ xử lý và tháng theo giờ Việt Nam.
        UUID t1 = ticket(jdbc, r2, "RESOLVED", "500000", "OWNER", null, "2026-10-01T00:00:00Z");
        event(jdbc, t1, "REPORTED", "2026-10-01T00:00:00Z");
        event(jdbc, t1, "RESOLVED", "2026-10-02T00:00:00Z");
        event(jdbc, t1, "RESOLVED", "2026-10-05T00:00:00Z");
        UUID t2 = ticket(jdbc, r2, "AWAITING_PAYMENT", "200000", "TENANT", null, "2026-10-03T00:00:00Z");
        event(jdbc, t2, "AWAITING_PAYMENT", "2026-10-03T12:00:00Z");
        // Phí sửa chữa của t2 đã gộp vào hóa đơn tháng 10: không được tính lại.
        Invoice fee = new Invoice(UUID.randomUUID(), contract.getId(), r1.getId(), tenantId, 10, 2026,
            new BigDecimal("200000"), new BigDecimal("200000"), BigDecimal.ZERO, LocalDate.of(2026, 10, 1),
            InvoiceStatus.ISSUED, null, InvoiceType.MAINTENANCE, t2, null, Instant.now(), Instant.now());
        fee.consolidateInto(october.getId());
        invoices.save(fee);
        UUID t5 = ticket(jdbc, r3, "RESOLVED", "100000", "OWNER", null, "2026-09-30T12:00:00Z");
        event(jdbc, t5, "RESOLVED", "2026-09-30T18:00:00Z");
        ticket(jdbc, r3, "REPORTED", "0", "OWNER", "2026-10-10T00:00:00Z", "2026-10-09T00:00:00Z");
        ticket(jdbc, r3, "IN_PROGRESS", "0", "OWNER", "2026-10-20T00:00:00Z", "2026-10-09T00:00:00Z");

        List<InvoiceAggregate> invoiceRows = stats.invoiceAggregates(List.of(p), YearMonth.of(2025, 11),
            YearMonth.of(2026, 10), LocalDate.of(2026, 10, 14));
        Map<YearMonth, InvoiceAggregate> byPeriod = invoiceRows.stream()
            .collect(Collectors.toMap(InvoiceAggregate::period, r -> r));
        assertEquals(3, invoiceRows.size());
        InvoiceAggregate sep = byPeriod.get(YearMonth.of(2026, 9));
        assertEquals(0, new BigDecimal("3200000").compareTo(sep.billed()));
        assertEquals(0, new BigDecimal("1000000").compareTo(sep.collected()));
        assertEquals(0, new BigDecimal("2200000").compareTo(sep.outstanding()));
        assertEquals(1, sep.overdueCount());
        assertEquals(0, new BigDecimal("2200000").compareTo(sep.overdueAmount()));
        InvoiceAggregate oct = byPeriod.get(YearMonth.of(2026, 10));
        assertEquals(1, oct.invoiceCount(), "phí đã gộp không tính lại");
        assertEquals(0, oct.overdueCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(byPeriod.get(YearMonth.of(2026, 8)).outstanding()));
        assertTrue(stats.invoiceAggregates(List.of(), YearMonth.of(2025, 11), YearMonth.of(2026, 10),
            LocalDate.of(2026, 10, 14)).isEmpty());
        assertTrue(stats.invoiceAggregates(null, YearMonth.of(2025, 11), YearMonth.of(2026, 10),
            LocalDate.of(2026, 10, 14)).stream().anyMatch(r -> r.propertyId().equals(p)), "phạm vi null là toàn hệ thống");

        assertEquals(3, stats.roomStatusCounts(List.of(p)).size());

        List<MaintenanceAggregate> done = stats.completedMaintenance(List.of(p),
            Instant.parse("2026-09-30T17:00:00Z"), Instant.parse("2026-10-31T17:00:00Z"));
        assertEquals(1, done.size(), "18:00 UTC 30/09 thuộc tháng 10 giờ Việt Nam");
        assertEquals(YearMonth.of(2026, 10), done.get(0).period());
        assertEquals(3, done.get(0).completedCount());
        assertEquals(0, new BigDecimal("600000").compareTo(done.get(0).ownerRepairCost()), "bỏ phí người thuê chịu");
        assertEquals(24 + 12 + 6, done.get(0).totalResolutionHours(), 0.01, "tính theo lần đầu sửa xong");

        List<OpenMaintenanceCount> open = stats.openMaintenance(List.of(p), Instant.parse("2026-10-14T05:00:00Z"));
        assertEquals(1, open.size());
        assertEquals(2, open.get(0).openCount());
        assertEquals(1, open.get(0).overdueCount());

        assertTrue(stats.propertyApprovalCounts().stream()
            .anyMatch(c -> c.status() == PropertyApprovalStatus.PENDING && c.count() >= 1));
        PendingPropertyRow pendingRow = stats.pendingProperties().stream()
            .filter(r -> r.id().equals(pending)).findFirst().orElseThrow();
        assertEquals("Chủ Thống Kê", pendingRow.ownerName(), "tên chủ nhà được giải mã");
        assertEquals("stats.owner@example.com", pendingRow.ownerEmail());

        assertTrue(stats.userCounts().stream()
            .anyMatch(c -> c.role() == UserRole.OWNER && c.status() == User.UserStatus.ACTIVE && c.count() >= 1));
        List<SignupCount> signups = stats.userSignups(Instant.parse("2030-02-28T17:00:00Z"),
            Instant.parse("2030-03-31T17:00:00Z"));
        assertEquals(List.of(new SignupCount(YearMonth.of(2030, 3), UserRole.OWNER, 1)), signups);

        jdbc.update("INSERT INTO audit_logs (action, actor_id, created_at) VALUES "
            + "('A', :actor, '2030-04-02T00:00:00Z'), ('B', :actor, '2030-04-03T00:00:00Z'), ('C', NULL, '2030-05-03T00:00:00Z')",
            new MapSqlParameterSource("actor", ownerId));
        List<ActivityCount> activity = stats.auditActivity(Instant.parse("2030-03-31T17:00:00Z"),
            Instant.parse("2030-04-30T17:00:00Z"));
        assertEquals(List.of(new ActivityCount(YearMonth.of(2030, 4), 2, 1)), activity);
    }

    private static Room room(UUID propertyId, String number, RoomStatus status) {
        return new Room(UUID.randomUUID(), propertyId, number, 1, null, new BigDecimal("3000000"), 2, status,
            Instant.now(), Instant.now());
    }

    private static Invoice invoice(Contract contract, int year, int month, String total, String paid,
                                   InvoiceStatus status, LocalDate due) {
        return new Invoice(UUID.randomUUID(), contract.getId(), contract.getRoomId(), contract.getPrimaryTenantId(),
            month, year, new BigDecimal(total), new BigDecimal(total), new BigDecimal(paid), due, status, null,
            InvoiceType.RENT, null, null, Instant.now(), Instant.now());
    }

    private static UUID ticket(NamedParameterJdbcTemplate jdbc, Room room, String status, String cost, String liable,
                               String dueAt, String createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO maintenance_tickets (id, room_id, title, description, priority, status, repair_cost,
                liable_party, due_at, created_at, updated_at)
            VALUES (:id, :room, 'Hỏng', 'mô tả', 'MEDIUM', :status, :cost, :liable, CAST(:due AS timestamptz),
                CAST(:created AS timestamptz), CAST(:created AS timestamptz))
            """, new MapSqlParameterSource()
            .addValue("id", id).addValue("room", room.getId()).addValue("status", status)
            .addValue("cost", new BigDecimal(cost)).addValue("liable", liable)
            .addValue("due", dueAt).addValue("created", createdAt));
        return id;
    }

    private static void event(NamedParameterJdbcTemplate jdbc, UUID ticketId, String toStatus, String at) {
        jdbc.update("INSERT INTO maintenance_ticket_events (ticket_id, to_status, created_at) "
                + "VALUES (:ticket, :to, CAST(:at AS timestamptz))",
            new MapSqlParameterSource().addValue("ticket", ticketId).addValue("to", toStatus).addValue("at", at));
    }
}
