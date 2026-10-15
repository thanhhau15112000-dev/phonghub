package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.domain.exception.OptimisticLockConflictException;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceCause;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.MaintenanceTicketEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresMaintenanceTicketRepository implements MaintenanceTicketRepositoryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresMaintenanceTicketRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<MaintenanceTicket> ROW_MAPPER = (rs, rowNum) -> {
        String tenantIdStr = rs.getString("requested_by_tenant_id");
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;
        String techIdStr = rs.getString("assigned_technician_id");
        UUID techId = techIdStr != null ? UUID.fromString(techIdStr) : null;
        String causeStr = rs.getString("cause_category");

        MaintenanceTicket ticket = new MaintenanceTicket(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("room_id")),
            UUID.fromString(rs.getString("property_id")),
            tenantId,
            techId,
            rs.getString("title"),
            rs.getString("description"),
            MaintenancePriority.valueOf(rs.getString("priority")),
            MaintenanceStatus.valueOf(rs.getString("status")),
            rs.getBigDecimal("repair_cost"),
            rs.getString("resolution_notes"),
            causeStr != null ? MaintenanceCause.valueOf(causeStr) : null,
            LiableParty.valueOf(rs.getString("liable_party")),
            rs.getString("reject_reason"),
            nullableInstant(rs.getTimestamp("due_at")),
            nullableInstant(rs.getTimestamp("sla_notified_at")),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
        );
        ticket.restoreVersion(rs.getLong("version"));
        return ticket;
    };

    private static Instant nullableInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }

    @Override
    public MaintenanceTicket save(MaintenanceTicket ticket) {
        String sql = """
            INSERT INTO maintenance_tickets (id, room_id, requested_by_tenant_id, assigned_technician_id, title, description, priority, status, repair_cost, resolution_notes, cause_category, liable_party, reject_reason, due_at, sla_notified_at, created_at, updated_at, version)
            VALUES (:id, :roomId, :tenantId, :technicianId, :title, :description, :priority, :status, :repairCost, :resolutionNotes, :causeCategory, :liableParty, :rejectReason, :dueAt, :slaNotifiedAt, :createdAt, :updatedAt, :version)
            ON CONFLICT (id) DO UPDATE SET
                assigned_technician_id = EXCLUDED.assigned_technician_id,
                title = EXCLUDED.title,
                description = EXCLUDED.description,
                priority = EXCLUDED.priority,
                status = EXCLUDED.status,
                repair_cost = EXCLUDED.repair_cost,
                resolution_notes = EXCLUDED.resolution_notes,
                liable_party = EXCLUDED.liable_party,
                reject_reason = EXCLUDED.reject_reason,
                due_at = EXCLUDED.due_at,
                sla_notified_at = EXCLUDED.sla_notified_at,
                updated_at = EXCLUDED.updated_at,
                version = maintenance_tickets.version + 1
            WHERE maintenance_tickets.version = :version
            RETURNING version
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", ticket.getId())
            .addValue("version", ticket.getVersion())
            .addValue("roomId", ticket.getRoomId())
            .addValue("tenantId", ticket.getRequestedByTenantId())
            .addValue("technicianId", ticket.getAssignedTechnicianId())
            .addValue("title", ticket.getTitle())
            .addValue("description", ticket.getDescription())
            .addValue("priority", ticket.getPriority().name())
            .addValue("status", ticket.getStatus().name())
            .addValue("repairCost", ticket.getRepairCost())
            .addValue("resolutionNotes", ticket.getResolutionNotes())
            .addValue("causeCategory", ticket.getCauseCategory() != null ? ticket.getCauseCategory().name() : null)
            .addValue("liableParty", ticket.getLiableParty().name())
            .addValue("rejectReason", ticket.getRejectionReason())
            .addValue("dueAt", ticket.getDueAt() == null ? null : Timestamp.from(ticket.getDueAt()))
            .addValue("slaNotifiedAt", ticket.getSlaNotifiedAt() == null ? null : Timestamp.from(ticket.getSlaNotifiedAt()))
            .addValue("createdAt", Timestamp.from(ticket.getCreatedAt()))
            .addValue("updatedAt", Timestamp.from(ticket.getUpdatedAt()));

        List<Long> versions = jdbcTemplate.queryForList(sql, params, Long.class);
        if (versions.isEmpty()) {
            throw new OptimisticLockConflictException("Phiếu bảo trì");
        }
        ticket.restoreVersion(versions.getFirst());
        for (MaintenanceTicketEvent event : ticket.drainEvents()) {
            jdbcTemplate.update("""
                INSERT INTO maintenance_ticket_events (ticket_id, from_status, to_status, actor_id, note, created_at)
                VALUES (:ticketId, :from, :to, :actor, :note, :at)
                """, new MapSqlParameterSource()
                .addValue("ticketId", event.ticketId())
                .addValue("from", event.fromStatus() == null ? null : event.fromStatus().name())
                .addValue("to", event.toStatus().name())
                .addValue("actor", event.actorId())
                .addValue("note", event.note())
                .addValue("at", Timestamp.from(event.at())));
        }
        return ticket;
    }

    @Override
    public List<MaintenanceTicketEvent> findEvents(UUID ticketId) {
        return jdbcTemplate.query("""
            SELECT * FROM maintenance_ticket_events WHERE ticket_id = :ticketId ORDER BY created_at, id
            """, Map.of("ticketId", ticketId), (rs, rowNum) -> new MaintenanceTicketEvent(
            rs.getObject("ticket_id", UUID.class),
            rs.getString("from_status") == null ? null : MaintenanceStatus.valueOf(rs.getString("from_status")),
            MaintenanceStatus.valueOf(rs.getString("to_status")),
            rs.getObject("actor_id", UUID.class),
            rs.getString("note"),
            rs.getTimestamp("created_at").toInstant()));
    }

    @Override
    public Optional<MaintenanceTicket> findById(UUID id) {
        String sql = """
            SELECT t.*, r.property_id FROM maintenance_tickets t
            JOIN rooms r ON t.room_id = r.id
            WHERE t.id = :id
            """;
        List<MaintenanceTicket> list = jdbcTemplate.query(sql, Map.of("id", id), ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public List<MaintenanceTicket> findByPropertyId(UUID propertyId) {
        String sql = """
            SELECT t.*, r.property_id FROM maintenance_tickets t
            JOIN rooms r ON t.room_id = r.id
            WHERE r.property_id = :propertyId
            ORDER BY t.created_at DESC
            """;
        return jdbcTemplate.query(sql, Map.of("propertyId", propertyId), ROW_MAPPER);
    }

    @Override
    public List<MaintenanceTicket> findByRoomId(UUID roomId) {
        String sql = """
            SELECT t.*, r.property_id FROM maintenance_tickets t
            JOIN rooms r ON t.room_id = r.id
            WHERE t.room_id = :roomId
            ORDER BY t.created_at DESC
            """;
        return jdbcTemplate.query(sql, Map.of("roomId", roomId), ROW_MAPPER);
    }

    @Override
    public List<MaintenanceTicket> findByRoomIdAndStatusNot(UUID roomId, MaintenanceStatus status) {
        String sql = """
            SELECT t.*, r.property_id FROM maintenance_tickets t
            JOIN rooms r ON t.room_id = r.id
            WHERE t.room_id = :roomId AND t.status != :status
            ORDER BY t.created_at DESC
            """;
        return jdbcTemplate.query(sql, Map.of("roomId", roomId, "status", status.name()), ROW_MAPPER);
    }

    @Override
    public List<MaintenanceTicket> findByAssignedTechnicianId(UUID technicianId) {
        String sql = """
            SELECT t.*, r.property_id FROM maintenance_tickets t
            JOIN rooms r ON t.room_id = r.id
            WHERE t.assigned_technician_id = :technicianId
            ORDER BY t.created_at DESC
            """;
        return jdbcTemplate.query(sql, Map.of("technicianId", technicianId), ROW_MAPPER);
    }

    @Override
    public List<MaintenanceTicket> findAll() {
        String sql = """
            SELECT t.*, r.property_id FROM maintenance_tickets t
            JOIN rooms r ON t.room_id = r.id
            ORDER BY t.created_at DESC
            """;
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }
}
