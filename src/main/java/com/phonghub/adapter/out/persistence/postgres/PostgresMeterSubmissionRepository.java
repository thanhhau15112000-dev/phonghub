package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.MeterSubmissionRepositoryPort;
import com.phonghub.domain.exception.MeterReadingConflictException;
import com.phonghub.domain.model.MeterSubmission;
import com.phonghub.domain.model.MeterType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresMeterSubmissionRepository implements MeterSubmissionRepositoryPort {

    private static final RowMapper<MeterSubmission> ROW_MAPPER = (rs, rowNum) -> new MeterSubmission(
        rs.getObject("id", UUID.class),
        rs.getObject("room_id", UUID.class),
        MeterType.valueOf(rs.getString("meter_type")),
        rs.getBigDecimal("declared_value"),
        rs.getString("photo_key"),
        rs.getString("photo_content_type"),
        rs.getBigDecimal("ocr_value"),
        rs.getBigDecimal("ocr_confidence"),
        rs.getString("ocr_engine"),
        MeterSubmission.MatchStatus.valueOf(rs.getString("match_status")),
        MeterSubmission.Status.valueOf(rs.getString("status")),
        rs.getObject("submitted_by", UUID.class),
        instant(rs.getTimestamp("submitted_at")),
        rs.getObject("reviewed_by", UUID.class),
        instant(rs.getTimestamp("reviewed_at")),
        rs.getString("review_note"),
        rs.getBigDecimal("final_value"),
        rs.getObject("reading_id", UUID.class));

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresMeterSubmissionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    @Override
    public MeterSubmission save(MeterSubmission s) {
        try {
            jdbcTemplate.update("""
                INSERT INTO meter_submissions (id, room_id, meter_type, declared_value, photo_key, photo_content_type,
                    ocr_value, ocr_confidence, ocr_engine, match_status, status, submitted_by, submitted_at,
                    reviewed_by, reviewed_at, review_note, final_value, reading_id)
                VALUES (:id, :roomId, :type, :declared, :photoKey, :photoType, :ocrValue, :ocrConfidence, :engine,
                    :match, :status, :submittedBy, :submittedAt, :reviewedBy, :reviewedAt, :note, :finalValue, :readingId)
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    reviewed_by = EXCLUDED.reviewed_by,
                    reviewed_at = EXCLUDED.reviewed_at,
                    review_note = EXCLUDED.review_note,
                    final_value = EXCLUDED.final_value,
                    reading_id = EXCLUDED.reading_id
                """, new MapSqlParameterSource()
                .addValue("id", s.id()).addValue("roomId", s.roomId()).addValue("type", s.meterType().name())
                .addValue("declared", s.declaredValue()).addValue("photoKey", s.photoKey())
                .addValue("photoType", s.photoContentType()).addValue("ocrValue", s.ocrValue())
                .addValue("ocrConfidence", s.ocrConfidence()).addValue("engine", s.ocrEngine())
                .addValue("match", s.matchStatus().name()).addValue("status", s.status().name())
                .addValue("submittedBy", s.submittedBy()).addValue("submittedAt", ts(s.submittedAt()))
                .addValue("reviewedBy", s.reviewedBy()).addValue("reviewedAt", ts(s.reviewedAt()))
                .addValue("note", s.reviewNote()).addValue("finalValue", s.finalValue())
                .addValue("readingId", s.readingId()));
        } catch (DuplicateKeyException ex) {
            throw new MeterReadingConflictException("Đã có một lượt gửi chỉ số đang chờ duyệt cho đồng hồ này");
        }
        return s;
    }

    @Override
    public Optional<MeterSubmission> findById(UUID id) {
        return jdbcTemplate.query("SELECT * FROM meter_submissions WHERE id = :id", Map.of("id", id), ROW_MAPPER)
            .stream().findFirst();
    }

    @Override
    public List<MeterSubmission> findByRoomId(UUID roomId) {
        return jdbcTemplate.query("SELECT * FROM meter_submissions WHERE room_id = :roomId ORDER BY submitted_at DESC",
            Map.of("roomId", roomId), ROW_MAPPER);
    }

    @Override
    public List<MeterSubmission> findByRoomIds(Collection<UUID> roomIds) {
        if (roomIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
            "SELECT * FROM meter_submissions WHERE room_id IN (:roomIds) ORDER BY submitted_at DESC",
            Map.of("roomIds", roomIds), ROW_MAPPER);
    }
}
