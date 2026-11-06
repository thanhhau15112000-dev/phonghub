package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.MaintenanceAttachmentRepositoryPort;
import com.phonghub.domain.exception.AttachmentLimitException;
import com.phonghub.domain.model.MaintenanceAttachment;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresMaintenanceAttachmentRepository implements MaintenanceAttachmentRepositoryPort {

    private static final RowMapper<MaintenanceAttachment> ROW_MAPPER = (rs, rowNum) -> new MaintenanceAttachment(
        rs.getObject("id", UUID.class),
        rs.getObject("ticket_id", UUID.class),
        MaintenanceAttachment.Kind.valueOf(rs.getString("kind")),
        rs.getString("storage_key"),
        rs.getString("content_type"),
        rs.getLong("size_bytes"),
        rs.getObject("uploaded_by", UUID.class),
        rs.getTimestamp("created_at").toInstant(),
        MaintenanceAttachment.Phase.valueOf(rs.getString("phase")),
        rs.getInt("round"));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresMaintenanceAttachmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public MaintenanceAttachment save(MaintenanceAttachment attachment) {
        try {
            jdbcTemplate.update("""
                INSERT INTO maintenance_attachments (id, ticket_id, kind, storage_key, content_type, size_bytes,
                    uploaded_by, created_at, phase, round)
                VALUES (:id, :ticketId, :kind, :key, :contentType, :size, :uploadedBy, :createdAt, :phase, :round)
                """, new MapSqlParameterSource()
                .addValue("id", attachment.id())
                .addValue("ticketId", attachment.ticketId())
                .addValue("kind", attachment.kind().name())
                .addValue("key", attachment.storageKey())
                .addValue("contentType", attachment.contentType())
                .addValue("size", attachment.sizeBytes())
                .addValue("uploadedBy", attachment.uploadedBy())
                .addValue("createdAt", Timestamp.from(attachment.createdAt()))
                .addValue("phase", attachment.phase().name())
                .addValue("round", attachment.round()));
        } catch (DuplicateKeyException ex) {
            throw new AttachmentLimitException("Phiếu đã có video");
        }
        return attachment;
    }

    @Override
    public Optional<MaintenanceAttachment> findById(UUID id) {
        return jdbcTemplate.query("SELECT * FROM maintenance_attachments WHERE id = :id", Map.of("id", id), ROW_MAPPER)
            .stream().findFirst();
    }

    @Override
    public List<MaintenanceAttachment> findByTicketId(UUID ticketId) {
        return jdbcTemplate.query("SELECT * FROM maintenance_attachments WHERE ticket_id = :ticketId ORDER BY created_at",
            Map.of("ticketId", ticketId), ROW_MAPPER);
    }

    @Override
    public void delete(UUID id) {
        jdbcTemplate.update("DELETE FROM maintenance_attachments WHERE id = :id", Map.of("id", id));
    }
}
