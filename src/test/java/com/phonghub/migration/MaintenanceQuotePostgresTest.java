package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresMaintenanceTicketRepository;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.MaintenanceTicketEvent;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
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

/** #83 / V40: báo giá được lưu, đọc lại, cập nhật; xác nhận của người thuê lưu trạng thái VERIFIED và timeline. */
@Testcontainers(disabledWithoutDocker = true)
class MaintenanceQuotePostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("báo giá lưu và khôi phục; ràng buộc số tiền không âm; xác nhận lưu VERIFIED")
    void quotePersists() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker").load().migrate();

        UUID roomId;
        UUID propertyId;
        UUID userId;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT id, property_id FROM rooms LIMIT 1");
            rs.next();
            roomId = UUID.fromString(rs.getString(1));
            propertyId = UUID.fromString(rs.getString(2));
            rs = st.executeQuery("SELECT id FROM users LIMIT 1");
            rs.next();
            userId = UUID.fromString(rs.getString(1));
        }
        PostgresMaintenanceTicketRepository repo = new PostgresMaintenanceTicketRepository(new NamedParameterJdbcTemplate(ds));

        MaintenanceTicket ticket = repo.save(MaintenanceTicket.create(roomId, propertyId, null, "Vòi rỉ", "Bồn rửa",
            MaintenancePriority.MEDIUM));
        assertNull(repo.findById(ticket.getId()).orElseThrow().getQuote());

        MaintenanceTicket loaded = repo.findById(ticket.getId()).orElseThrow();
        loaded.quote(new BigDecimal("450000"), "Thay vòi mới", userId);
        loaded.attributeEventsTo(userId);
        repo.save(loaded);

        MaintenanceTicket.Quote quote = repo.findById(ticket.getId()).orElseThrow().getQuote();
        assertEquals(0, new BigDecimal("450000").compareTo(quote.amount()));
        assertEquals("Thay vòi mới", quote.note());
        assertEquals(userId, quote.quotedBy());
        assertTrue(quote.quotedAt() != null);

        MaintenanceTicket again = repo.findById(ticket.getId()).orElseThrow();
        again.quote(new BigDecimal("500000"), null, userId);
        repo.save(again);
        MaintenanceTicket.Quote updated = repo.findById(ticket.getId()).orElseThrow().getQuote();
        assertEquals(0, new BigDecimal("500000").compareTo(updated.amount()));
        assertNull(updated.note());

        List<MaintenanceTicketEvent> events = repo.findEvents(ticket.getId());
        assertTrue(events.stream().anyMatch(e -> "Báo giá 450.000 đ: Thay vòi mới".equals(e.note())
            && userId.equals(e.actorId())));

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            assertThrows(Exception.class, () -> st.execute("UPDATE maintenance_tickets SET quote_amount = -1 WHERE id = '"
                + ticket.getId() + "'"), "số tiền báo giá âm bị chặn ở DB");
            st.execute("UPDATE maintenance_tickets SET status = 'RESOLVED' WHERE id = '" + ticket.getId() + "'");
        }
        MaintenanceTicket resolved = repo.findById(ticket.getId()).orElseThrow();
        resolved.verifyByTenant();
        repo.save(resolved);
        assertEquals(MaintenanceStatus.VERIFIED, repo.findById(ticket.getId()).orElseThrow().getStatus());
        assertEquals(0, new BigDecimal("500000").compareTo(repo.findById(ticket.getId()).orElseThrow().getQuote().amount()),
            "các lần lưu sau không làm mất báo giá");
    }
}
