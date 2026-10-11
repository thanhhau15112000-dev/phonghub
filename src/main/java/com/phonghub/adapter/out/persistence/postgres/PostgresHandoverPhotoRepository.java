package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.HandoverPhotoRepositoryPort;
import com.phonghub.domain.model.HandoverPhoto;
import com.phonghub.domain.model.HandoverPhotoKind;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresHandoverPhotoRepository implements HandoverPhotoRepositoryPort {

    private static final RowMapper<HandoverPhoto> ROW_MAPPER = (rs, rowNum) -> new HandoverPhoto(
        rs.getObject("id", UUID.class),
        rs.getObject("handover_id", UUID.class),
        HandoverPhotoKind.valueOf(rs.getString("kind")),
        rs.getString("image_url"),
        rs.getString("content_type"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getObject("created_by", UUID.class)
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresHandoverPhotoRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public HandoverPhoto save(HandoverPhoto photo) {
        jdbcTemplate.update("""
            INSERT INTO handover_photos (id, handover_id, kind, image_url, content_type, created_at, created_by,
                updated_at, updated_by)
            VALUES (:id, :handoverId, :kind, :imageKey, :contentType, :createdAt, :createdBy, :createdAt, :createdBy)
            """, new MapSqlParameterSource()
            .addValue("id", photo.id())
            .addValue("handoverId", photo.handoverId())
            .addValue("kind", photo.kind().name())
            .addValue("imageKey", photo.imageKey())
            .addValue("contentType", photo.contentType())
            .addValue("createdAt", Timestamp.from(photo.createdAt()))
            .addValue("createdBy", photo.createdBy()));
        return photo;
    }

    @Override
    public Optional<HandoverPhoto> findById(UUID id) {
        List<HandoverPhoto> rows = jdbcTemplate.query("SELECT * FROM handover_photos WHERE id = :id",
            Map.of("id", id), ROW_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<HandoverPhoto> findByHandoverId(UUID handoverId) {
        return jdbcTemplate.query("SELECT * FROM handover_photos WHERE handover_id = :handoverId ORDER BY created_at",
            Map.of("handoverId", handoverId), ROW_MAPPER);
    }

    @Override
    public void delete(UUID id) {
        jdbcTemplate.update("DELETE FROM handover_photos WHERE id = :id", Map.of("id", id));
    }
}
