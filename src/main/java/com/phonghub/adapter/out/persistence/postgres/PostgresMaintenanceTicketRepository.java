package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.domain.exception.OptimisticLockConflictException;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceCause;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceQuoteVersion;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.MaintenanceTicketEvent;
import java.math.BigDecimal;
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
        java.math.BigDecimal quoteAmount = rs.getBigDecimal("quote_amount");
        if (quoteAmount != null) {
            String quotedBy = rs.getString("quoted_by");
            ticket.restoreQuote(new MaintenanceTicket.Quote(quoteAmount, rs.getString("quote_note"),
                quotedBy != null ? UUID.fromString(quotedBy) : null, nullableInstant(rs.getTimestamp("quoted_at"))));
        }
        ticket.restoreAssignmentTimeoutState(nullableInstant(rs.getTimestamp("assigned_at")),
            nullableInstant(rs.getTimestamp("understaffed_notified_at")));
        String resume = rs.getString("resume_status");
        BigDecimal approvedTotal = rs.getBigDecimal("approved_quote_total");
        ticket.restoreApprovalState(new MaintenanceTicket.ApprovalState(
            nullableInstant(rs.getTimestamp("branch_confirmed_at")),
            rs.getObject("branch_confirmed_by", UUID.class),
            nullableInstant(rs.getTimestamp("approval_due_at")),
            nullableInstant(rs.getTimestamp("owner_approved_at")),
            nullableInstant(rs.getTimestamp("approval_escalated_at")),
            nullableInstant(rs.getTimestamp("quote_due_at")),
            nullableInstant(rs.getTimestamp("quote_approved_at")),
            approvedTotal,
            rs.getInt("negotiation_rounds"),
            rs.getInt("quote_version"),
            resume == null ? null : MaintenanceStatus.valueOf(resume),
            nullableInstant(rs.getTimestamp("approval_reminder_sent_at")),
            rs.getBoolean("requote_requested")));
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
            INSERT INTO maintenance_tickets (id, room_id, requested_by_tenant_id, assigned_technician_id, title, description, priority, status, repair_cost, resolution_notes, cause_category, liable_party, reject_reason, due_at, sla_notified_at, assigned_at, understaffed_notified_at, quote_amount, quote_note, quoted_by, quoted_at, branch_confirmed_at, branch_confirmed_by, approval_due_at, owner_approved_at, approval_escalated_at, quote_due_at, quote_approved_at, approved_quote_total, negotiation_rounds, quote_version, resume_status, approval_reminder_sent_at, requote_requested, created_at, updated_at, version)
            VALUES (:id, :roomId, :tenantId, :technicianId, :title, :description, :priority, :status, :repairCost, :resolutionNotes, :causeCategory, :liableParty, :rejectReason, :dueAt, :slaNotifiedAt, :assignedAt, :understaffedNotifiedAt, :quoteAmount, :quoteNote, :quotedBy, :quotedAt, :branchConfirmedAt, :branchConfirmedBy, :approvalDueAt, :ownerApprovedAt, :approvalEscalatedAt, :quoteDueAt, :quoteApprovedAt, :approvedQuoteTotal, :negotiationRounds, :quoteVersion, :resumeStatus, :reminderSentAt, :requoteRequested, :createdAt, :updatedAt, :version)
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
                assigned_at = EXCLUDED.assigned_at,
                understaffed_notified_at = EXCLUDED.understaffed_notified_at,
                quote_amount = EXCLUDED.quote_amount,
                quote_note = EXCLUDED.quote_note,
                quoted_by = EXCLUDED.quoted_by,
                quoted_at = EXCLUDED.quoted_at,
                branch_confirmed_at = EXCLUDED.branch_confirmed_at,
                branch_confirmed_by = EXCLUDED.branch_confirmed_by,
                approval_due_at = EXCLUDED.approval_due_at,
                owner_approved_at = EXCLUDED.owner_approved_at,
                approval_escalated_at = EXCLUDED.approval_escalated_at,
                quote_due_at = EXCLUDED.quote_due_at,
                quote_approved_at = EXCLUDED.quote_approved_at,
                approved_quote_total = EXCLUDED.approved_quote_total,
                negotiation_rounds = EXCLUDED.negotiation_rounds,
                quote_version = EXCLUDED.quote_version,
                resume_status = EXCLUDED.resume_status,
                approval_reminder_sent_at = EXCLUDED.approval_reminder_sent_at,
                requote_requested = EXCLUDED.requote_requested,
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
            .addValue("assignedAt", ticket.getAssignedAt() == null ? null : Timestamp.from(ticket.getAssignedAt()))
            .addValue("understaffedNotifiedAt", ticket.getUnderstaffedNotifiedAt() == null ? null
                : Timestamp.from(ticket.getUnderstaffedNotifiedAt()))
            .addValue("quoteAmount", ticket.getQuote() == null ? null : ticket.getQuote().amount())
            .addValue("quoteNote", ticket.getQuote() == null ? null : ticket.getQuote().note())
            .addValue("quotedBy", ticket.getQuote() == null ? null : ticket.getQuote().quotedBy())
            .addValue("quotedAt", ticket.getQuote() == null || ticket.getQuote().quotedAt() == null ? null
                : Timestamp.from(ticket.getQuote().quotedAt()))
            .addValue("createdAt", Timestamp.from(ticket.getCreatedAt()))
            .addValue("updatedAt", Timestamp.from(ticket.getUpdatedAt()));
        MaintenanceTicket.ApprovalState approval = ticket.approvalState();
        params.addValue("branchConfirmedAt", ts(approval.branchConfirmedAt()))
            .addValue("branchConfirmedBy", approval.branchConfirmedBy())
            .addValue("approvalDueAt", ts(approval.approvalDueAt()))
            .addValue("ownerApprovedAt", ts(approval.ownerApprovedAt()))
            .addValue("approvalEscalatedAt", ts(approval.approvalEscalatedAt()))
            .addValue("quoteDueAt", ts(approval.quoteDueAt()))
            .addValue("quoteApprovedAt", ts(approval.quoteApprovedAt()))
            .addValue("approvedQuoteTotal", approval.approvedQuoteTotal())
            .addValue("negotiationRounds", approval.negotiationRounds())
            .addValue("quoteVersion", approval.quoteVersion())
            .addValue("resumeStatus", approval.resumeStatus() == null ? null : approval.resumeStatus().name())
            .addValue("reminderSentAt", ts(approval.reminderSentAt()))
            .addValue("requoteRequested", approval.requoteRequested());

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

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    @Override
    public MaintenanceQuoteVersion saveQuoteVersion(MaintenanceQuoteVersion version) {
        jdbcTemplate.update("""
            INSERT INTO maintenance_quote_versions (id, ticket_id, version, labor_amount, total_amount, note, quoted_by,
                quoted_at, status, tenant_note, decided_at)
            VALUES (:id, :ticketId, :version, :labor, :total, :note, :quotedBy, :quotedAt, :status, :tenantNote, :decidedAt)
            ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, tenant_note = EXCLUDED.tenant_note,
                decided_at = EXCLUDED.decided_at
            """, new MapSqlParameterSource()
            .addValue("id", version.id())
            .addValue("ticketId", version.ticketId())
            .addValue("version", version.version())
            .addValue("labor", version.laborAmount())
            .addValue("total", version.totalAmount())
            .addValue("note", version.note())
            .addValue("quotedBy", version.quotedBy())
            .addValue("quotedAt", Timestamp.from(version.quotedAt()))
            .addValue("status", version.status().name())
            .addValue("tenantNote", version.tenantNote())
            .addValue("decidedAt", ts(version.decidedAt())));
        Integer existingLines = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM maintenance_quote_lines WHERE quote_id = :id", Map.of("id", version.id()),
            Integer.class);
        if (existingLines != null && existingLines == 0) {
            int position = 0;
            for (MaintenanceQuoteVersion.Line line : version.lines()) {
                jdbcTemplate.update("""
                    INSERT INTO maintenance_quote_lines (quote_id, position, name, quantity, unit_price)
                    VALUES (:quoteId, :position, :name, :quantity, :unitPrice)
                    """, new MapSqlParameterSource()
                    .addValue("quoteId", version.id())
                    .addValue("position", position++)
                    .addValue("name", line.name())
                    .addValue("quantity", line.quantity())
                    .addValue("unitPrice", line.unitPrice()));
            }
        }
        return version;
    }

    @Override
    public List<MaintenanceQuoteVersion> findQuoteVersions(UUID ticketId) {
        List<MaintenanceQuoteVersion> headers = jdbcTemplate.query("""
            SELECT * FROM maintenance_quote_versions WHERE ticket_id = :ticketId ORDER BY version
            """, Map.of("ticketId", ticketId), (rs, rowNum) -> new MaintenanceQuoteVersion(
            rs.getObject("id", UUID.class), rs.getObject("ticket_id", UUID.class), rs.getInt("version"),
            List.of(), rs.getBigDecimal("labor_amount"), rs.getBigDecimal("total_amount"), rs.getString("note"),
            rs.getObject("quoted_by", UUID.class), rs.getTimestamp("quoted_at").toInstant(),
            MaintenanceQuoteVersion.Status.valueOf(rs.getString("status")), rs.getString("tenant_note"),
            nullableInstant(rs.getTimestamp("decided_at"))));
        List<MaintenanceQuoteVersion> result = new java.util.ArrayList<>();
        for (MaintenanceQuoteVersion header : headers) {
            List<MaintenanceQuoteVersion.Line> lines = jdbcTemplate.query("""
                SELECT name, quantity, unit_price FROM maintenance_quote_lines WHERE quote_id = :id ORDER BY position
                """, Map.of("id", header.id()), (rs, rowNum) -> new MaintenanceQuoteVersion.Line(
                rs.getString("name"), rs.getInt("quantity"), rs.getBigDecimal("unit_price")));
            result.add(new MaintenanceQuoteVersion(header.id(), header.ticketId(), header.version(), lines,
                header.laborAmount(), header.totalAmount(), header.note(), header.quotedBy(), header.quotedAt(),
                header.status(), header.tenantNote(), header.decidedAt()));
        }
        return result;
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
