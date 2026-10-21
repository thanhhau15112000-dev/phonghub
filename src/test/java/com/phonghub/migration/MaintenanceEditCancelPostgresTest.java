package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresMaintenanceTicketRepository;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
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

/** #89 trên Postgres thật: V46 cho phép CANCELLED; nội dung sửa và mốc hủy được lưu. */
@Testcontainers(disabledWithoutDocker = true)
class MaintenanceEditCancelPostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("Sửa tiêu đề/mức độ/hạn rồi hủy: đọc lại đúng, dòng thời gian có mốc hủy, DB chặn trạng thái lạ")
    void editAndCancelRoundTrip() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .load().migrate();
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        PostgresMaintenanceTicketRepository tickets = new PostgresMaintenanceTicketRepository(jdbc);
        Map<String, Object> room = jdbc.queryForMap("SELECT id, property_id FROM rooms LIMIT 1", Map.of());

        MaintenanceTicket ticket = tickets.save(MaintenanceTicket.create((UUID) room.get("id"),
            (UUID) room.get("property_id"), null, "Đèn hỏng", "Đèn hành lang không sáng", MaintenancePriority.LOW,
            null, Duration.ofHours(72)));

        MaintenanceTicket loaded = tickets.findById(ticket.getId()).orElseThrow();
        loaded.updateRequest("Đèn hỏng cả dãy", "Cả dãy hành lang tối", MaintenancePriority.URGENT, Duration.ofHours(4));
        tickets.save(loaded);
        MaintenanceTicket edited = tickets.findById(ticket.getId()).orElseThrow();
        assertEquals("Đèn hỏng cả dãy", edited.getTitle());
        assertEquals("Cả dãy hành lang tối", edited.getDescription());
        assertEquals(MaintenancePriority.URGENT, edited.getPriority());
        assertEquals(edited.getCreatedAt().plus(Duration.ofHours(4)).truncatedTo(ChronoUnit.MILLIS),
            edited.getDueAt().truncatedTo(ChronoUnit.MILLIS));

        edited.cancel("Trùng phiếu");
        tickets.save(edited);
        assertEquals(MaintenanceStatus.CANCELLED, tickets.findById(ticket.getId()).orElseThrow().getStatus());
        assertTrue(tickets.findEvents(ticket.getId()).stream()
            .anyMatch(e -> e.toStatus() == MaintenanceStatus.CANCELLED && "Hủy phiếu: Trùng phiếu".equals(e.note())));

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "UPDATE maintenance_tickets SET status = 'DELETED' WHERE id = :id", Map.of("id", ticket.getId())));
    }
}
