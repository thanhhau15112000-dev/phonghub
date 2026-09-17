package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresRoomRepository implements RoomRepositoryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresRoomRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Room> ROW_MAPPER = (rs, rowNum) -> new Room(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("property_id")),
        rs.getString("room_number"),
        rs.getInt("floor"),
        rs.getBigDecimal("area_sqm"),
        rs.getBigDecimal("base_price"),
        rs.getInt("max_occupants"),
        RoomStatus.valueOf(rs.getString("status")),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("updated_at"))
    );

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }

    @Override
    public Room save(Room room) {
        String sql = """
            INSERT INTO rooms (id, property_id, room_number, floor, area_sqm, base_price, max_occupants, status, created_at, updated_at)
            VALUES (:id, :propertyId, :roomNumber, :floor, :areaSqm, :basePrice, :maxOccupants, :status, :createdAt, :updatedAt)
            ON CONFLICT (id) DO UPDATE SET
                room_number = EXCLUDED.room_number,
                floor = EXCLUDED.floor,
                area_sqm = EXCLUDED.area_sqm,
                base_price = EXCLUDED.base_price,
                max_occupants = EXCLUDED.max_occupants,
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", room.getId())
            .addValue("propertyId", room.getPropertyId())
            .addValue("roomNumber", room.getRoomNumber())
            .addValue("floor", room.getFloor())
            .addValue("areaSqm", room.getAreaSqm())
            .addValue("basePrice", room.getBasePrice())
            .addValue("maxOccupants", room.getMaxOccupants())
            .addValue("status", room.getStatus().name())
            .addValue("createdAt", Timestamp.from(room.getCreatedAt()))
            .addValue("updatedAt", Timestamp.from(room.getUpdatedAt()));

        jdbcTemplate.update(sql, params);
        return room;
    }

    @Override
    public Optional<Room> findById(UUID id) {
        String sql = "SELECT * FROM rooms WHERE id = :id";
        List<Room> list = jdbcTemplate.query(sql, Map.of("id", id), ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public List<Room> findByPropertyId(UUID propertyId) {
        String sql = "SELECT * FROM rooms WHERE property_id = :propertyId ORDER BY room_number ASC";
        return jdbcTemplate.query(sql, Map.of("propertyId", propertyId), ROW_MAPPER);
    }

    @Override
    public List<Room> findByPropertyIdAndStatus(UUID propertyId, RoomStatus status) {
        String sql = "SELECT * FROM rooms WHERE property_id = :propertyId AND status = :status ORDER BY room_number ASC";
        return jdbcTemplate.query(sql, Map.of("propertyId", propertyId, "status", status.name()), ROW_MAPPER);
    }

    @Override
    public Optional<Room> findByPropertyIdAndRoomNumber(UUID propertyId, String roomNumber) {
        String sql = "SELECT * FROM rooms WHERE property_id = :propertyId AND LOWER(room_number) = LOWER(:roomNumber)";
        List<Room> list = jdbcTemplate.query(sql, Map.of("propertyId", propertyId, "roomNumber", roomNumber.trim()), ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public List<Room> findAllById(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT * FROM rooms WHERE id IN (:ids)";
        return jdbcTemplate.query(sql, Map.of("ids", ids), ROW_MAPPER);
    }

    @Override
    public boolean existsById(UUID id) {
        String sql = "SELECT COUNT(*) FROM rooms WHERE id = :id";
        Integer count = jdbcTemplate.queryForObject(sql, Map.of("id", id), Integer.class);
        return count != null && count > 0;
    }
}
