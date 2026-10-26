package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresMaintenanceTicketRepository;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceCause;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceQuoteVersion;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
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

/** V56 (#202, #203): trạng thái chốt nhánh, hạn duyệt và các phiên bản báo giá được lưu và đọc lại đúng. */
@Testcontainers(disabledWithoutDocker = true)
class MaintenanceBranchApprovalPostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("approval state, status values and quote versions with lines round-trip; constraints reject bad data")
    void approvalStateAndQuoteVersionsPersist() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker").load().migrate();

        UUID roomId;
        UUID propertyId;
        UUID userId;
        UUID tenantId;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT id, property_id FROM rooms LIMIT 1");
            rs.next();
            roomId = UUID.fromString(rs.getString(1));
            propertyId = UUID.fromString(rs.getString(2));
            rs = st.executeQuery("SELECT id FROM users LIMIT 1");
            rs.next();
            userId = UUID.fromString(rs.getString(1));
            rs = st.executeQuery("SELECT id FROM tenants LIMIT 1");
            rs.next();
            tenantId = UUID.fromString(rs.getString(1));
        }
        PostgresMaintenanceTicketRepository repo = new PostgresMaintenanceTicketRepository(new NamedParameterJdbcTemplate(ds));

        // Nhánh A: chờ chủ trọ duyệt có hạn
        MaintenanceTicket wear = repo.save(MaintenanceTicket.create(roomId, propertyId, tenantId, "Bóng đèn", "Cháy bóng",
            MaintenancePriority.LOW, MaintenanceCause.NATURAL_WEAR));
        wear.confirmBranch(LiableParty.OWNER, null, userId, Duration.ofDays(3));
        repo.save(wear);
        MaintenanceTicket loadedWear = repo.findById(wear.getId()).orElseThrow();
        assertEquals(MaintenanceStatus.PENDING_OWNER_APPROVAL, loadedWear.getStatus());
        assertTrue(loadedWear.isBranchConfirmed());
        assertEquals(userId, loadedWear.getBranchConfirmedBy());
        assertNotNull(loadedWear.getApprovalDueAt());
        assertTrue(loadedWear.escalateOverdueApproval(loadedWear.getApprovalDueAt()));
        repo.save(loadedWear);
        MaintenanceTicket escalated = repo.findById(wear.getId()).orElseThrow();
        assertEquals(MaintenanceStatus.REPORTED, escalated.getStatus());
        assertNotNull(escalated.getApprovalEscalatedAt());

        // Nhánh B: báo giá nhiều phiên bản kèm dòng linh kiện
        MaintenanceTicket fault = repo.save(MaintenanceTicket.create(roomId, propertyId, tenantId, "Vỡ kính", "Kính cửa sổ",
            MaintenancePriority.MEDIUM, MaintenanceCause.TENANT_USAGE));
        fault.confirmBranch(LiableParty.TENANT, null, userId, Duration.ofDays(3));
        MaintenanceQuoteVersion v1 = fault.submitQuote(
            List.of(new MaintenanceQuoteVersion.Line("Kính 5mm", 2, new BigDecimal("150000"))),
            new BigDecimal("80000"), "Gồm công lắp", userId, Duration.ofDays(7));
        fault.attributeEventsTo(userId);
        repo.save(fault);
        repo.saveQuoteVersion(v1);

        MaintenanceTicket pending = repo.findById(fault.getId()).orElseThrow();
        assertEquals(MaintenanceStatus.PENDING_TENANT_APPROVAL, pending.getStatus());
        assertEquals(1, pending.getQuoteVersion());
        assertNotNull(pending.getQuoteDueAt());
        pending.tenantNegotiateQuote("Giảm công lắp");
        repo.save(pending);
        repo.saveQuoteVersion(v1.decided(MaintenanceQuoteVersion.Status.NEGOTIATED, "Giảm công lắp", Instant.now()));

        MaintenanceTicket negotiated = repo.findById(fault.getId()).orElseThrow();
        assertEquals(MaintenanceStatus.REPORTED, negotiated.getStatus());
        assertEquals(1, negotiated.getNegotiationRounds());
        assertTrue(negotiated.isAwaitingRequote());
        assertNull(negotiated.getQuoteDueAt());

        List<MaintenanceQuoteVersion> versions = repo.findQuoteVersions(fault.getId());
        assertEquals(1, versions.size());
        assertEquals(MaintenanceQuoteVersion.Status.NEGOTIATED, versions.getFirst().status());
        assertEquals("Giảm công lắp", versions.getFirst().tenantNote());
        assertEquals(0, new BigDecimal("380000").compareTo(versions.getFirst().totalAmount()));
        assertEquals(1, versions.getFirst().lines().size());
        assertEquals("Kính 5mm", versions.getFirst().lines().getFirst().name());
        assertEquals(2, versions.getFirst().lines().getFirst().quantity());

        // Lưu lại phiên bản không nhân đôi dòng linh kiện
        repo.saveQuoteVersion(versions.getFirst());
        assertEquals(1, repo.findQuoteVersions(fault.getId()).getFirst().lines().size());

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            assertThrows(Exception.class, () -> st.execute(
                "UPDATE maintenance_tickets SET negotiation_rounds = 3 WHERE id = '" + fault.getId() + "'"),
                "tối đa 2 vòng thương lượng được chặn ở DB");
            assertThrows(Exception.class, () -> st.execute(
                "UPDATE maintenance_tickets SET status = 'UNKNOWN_STATUS' WHERE id = '" + fault.getId() + "'"));
        }
    }
}
