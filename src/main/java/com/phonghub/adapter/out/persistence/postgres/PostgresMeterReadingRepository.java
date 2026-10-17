package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.MeterReadingRepositoryPort;
import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresMeterReadingRepository implements MeterReadingRepositoryPort {

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static final RowMapper<MeterReading> ROW_MAPPER = (rs, rowNum) -> new MeterReading(
        rs.getObject("id", UUID.class),
        rs.getObject("room_id", UUID.class),
        rs.getObject("contract_id", UUID.class),
        MeterType.valueOf(rs.getString("meter_type")),
        rs.getInt("period_year"),
        rs.getInt("period_month"),
        rs.getBigDecimal("previous_value"),
        rs.getBigDecimal("current_value"),
        rs.getBoolean("replaced"),
        rs.getBigDecimal("old_meter_final_value"),
        rs.getBigDecimal("new_meter_start_value"),
        rs.getString("replacement_note"),
        rs.getBigDecimal("consumption"),
        rs.getDate("reading_date").toLocalDate(),
        rs.getString("photo_url"),
        rs.getBigDecimal("ocr_value"),
        rs.getBigDecimal("ocr_confidence"),
        instant(rs.getTimestamp("created_at")),
        rs.getObject("created_by", UUID.class),
        instant(rs.getTimestamp("updated_at")),
        rs.getObject("updated_by", UUID.class)
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresMeterReadingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public MeterReading save(MeterReading reading) {
        jdbcTemplate.update("""
            INSERT INTO meter_readings (id, room_id, contract_id, meter_type, period_year, period_month,
                previous_value, current_value, replaced, old_meter_final_value, new_meter_start_value,
                replacement_note, consumption, reading_date, photo_url, ocr_value, ocr_confidence, created_at, created_by, updated_at, updated_by)
            VALUES (:id, :roomId, :contractId, :type, :year, :month, :previous, :current, :replaced, :oldFinal,
                :newStart, :note, :consumption, :date, :photo, :ocrValue, :ocrConfidence, :createdAt, :createdBy, :updatedAt, :updatedBy)
            ON CONFLICT (id) DO UPDATE SET
                photo_url = EXCLUDED.photo_url,
                updated_at = EXCLUDED.updated_at,
                updated_by = EXCLUDED.updated_by
            """, new MapSqlParameterSource()
            .addValue("id", reading.id())
            .addValue("roomId", reading.roomId())
            .addValue("contractId", reading.contractId())
            .addValue("type", reading.meterType().name())
            .addValue("year", reading.periodYear())
            .addValue("month", reading.periodMonth())
            .addValue("previous", reading.previousValue())
            .addValue("current", reading.currentValue())
            .addValue("replaced", reading.replaced())
            .addValue("oldFinal", reading.oldMeterFinalValue())
            .addValue("newStart", reading.newMeterStartValue())
            .addValue("note", reading.replacementNote())
            .addValue("consumption", reading.consumption())
            .addValue("date", java.sql.Date.valueOf(reading.readingDate()))
            .addValue("photo", reading.photoKey())
            .addValue("ocrValue", reading.ocrValue())
            .addValue("ocrConfidence", reading.ocrConfidence())
            .addValue("createdAt", Timestamp.from(reading.createdAt()))
            .addValue("createdBy", reading.createdBy())
            .addValue("updatedAt", Timestamp.from(reading.updatedAt()))
            .addValue("updatedBy", reading.updatedBy()));
        return reading;
    }

    @Override
    public Optional<MeterReading> findById(UUID id) {
        List<MeterReading> rows = jdbcTemplate.query("SELECT * FROM meter_readings WHERE id = :id",
            Map.of("id", id), ROW_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<MeterReading> findByRoomId(UUID roomId) {
        return jdbcTemplate.query("""
            SELECT * FROM meter_readings WHERE room_id = :roomId
            ORDER BY period_year DESC, period_month DESC, meter_type
            """, Map.of("roomId", roomId), ROW_MAPPER);
    }

    @Override
    public Optional<MeterReading> findLatest(UUID roomId, MeterType type) {
        List<MeterReading> rows = jdbcTemplate.query("""
            SELECT * FROM meter_readings WHERE room_id = :roomId AND meter_type = :type
            ORDER BY period_year DESC, period_month DESC LIMIT 1
            """, Map.of("roomId", roomId, "type", type.name()), ROW_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<MeterReading> findByPeriod(UUID roomId, MeterType type, int year, int month) {
        List<MeterReading> rows = jdbcTemplate.query("""
            SELECT * FROM meter_readings WHERE room_id = :roomId AND meter_type = :type
              AND period_year = :year AND period_month = :month
            """, Map.of("roomId", roomId, "type", type.name(), "year", year, "month", month), ROW_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }
}
