package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.domain.exception.OptimisticLockConflictException;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomPage;
import com.phonghub.domain.model.RoomSearchCriteria;
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

    private static final RowMapper<Room> ROW_MAPPER = (rs, rowNum) -> withState(new Room(
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
    ), rs.getLong("version"), rs.getTimestamp("archived_at"));

    private static Room withState(Room room, long version, Timestamp archivedAt) {
        room.restoreVersion(version);
        room.restoreArchivedAt(archivedAt == null ? null : archivedAt.toInstant());
        return room;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }

    @Override
    public Room save(Room room) {
        String sql = """
            INSERT INTO rooms (id, property_id, room_number, floor, area_sqm, base_price, max_occupants, status, created_at, updated_at, version, archived_at)
            VALUES (:id, :propertyId, :roomNumber, :floor, :areaSqm, :basePrice, :maxOccupants, :status, :createdAt, :updatedAt, :version, :archivedAt)
            ON CONFLICT (id) DO UPDATE SET
                room_number = EXCLUDED.room_number,
                floor = EXCLUDED.floor,
                area_sqm = EXCLUDED.area_sqm,
                base_price = EXCLUDED.base_price,
                max_occupants = EXCLUDED.max_occupants,
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at,
                archived_at = EXCLUDED.archived_at,
                version = rooms.version + 1
            WHERE rooms.version = :version
            RETURNING version
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", room.getId())
            .addValue("version", room.getVersion())
            .addValue("propertyId", room.getPropertyId())
            .addValue("roomNumber", room.getRoomNumber())
            .addValue("floor", room.getFloor())
            .addValue("areaSqm", room.getAreaSqm())
            .addValue("basePrice", room.getBasePrice())
            .addValue("maxOccupants", room.getMaxOccupants())
            .addValue("status", room.getStatus().name())
            .addValue("createdAt", Timestamp.from(room.getCreatedAt()))
            .addValue("updatedAt", Timestamp.from(room.getUpdatedAt()))
            .addValue("archivedAt", room.getArchivedAt() == null ? null : Timestamp.from(room.getArchivedAt()));

        List<Long> versions = jdbcTemplate.queryForList(sql, params, Long.class);
        if (versions.isEmpty()) {
            throw new OptimisticLockConflictException("Room");
        }
        room.restoreVersion(versions.getFirst());
        return room;
    }

    @Override
    public RoomPage search(RoomSearchCriteria c) {
        if (c.restrictToRoomIds() != null && c.restrictToRoomIds().isEmpty()) {
            return new RoomPage(List.of(), 0, c.page(), c.size());
        }
        StringBuilder where = new StringBuilder(" WHERE property_id = :propertyId")
            .append(c.archived() ? " AND archived_at IS NOT NULL" : " AND archived_at IS NULL");
        MapSqlParameterSource params = new MapSqlParameterSource("propertyId", c.propertyId());
        if (c.status() != null) {
            where.append(" AND status = :status");
            params.addValue("status", c.status().name());
        }
        if (c.minPrice() != null) {
            where.append(" AND base_price >= :minPrice");
            params.addValue("minPrice", c.minPrice());
        }
        if (c.maxPrice() != null) {
            where.append(" AND base_price <= :maxPrice");
            params.addValue("maxPrice", c.maxPrice());
        }
        if (c.floor() != null) {
            where.append(" AND floor = :floor");
            params.addValue("floor", c.floor());
        }
        if (c.restrictToRoomIds() != null) {
            where.append(" AND id IN (:roomIds)");
            params.addValue("roomIds", c.restrictToRoomIds());
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rooms" + where, params, Long.class);
        String sql = "SELECT * FROM rooms" + where + " ORDER BY floor ASC, room_number ASC";
        if (c.size() != null) {
            sql += " LIMIT :limit OFFSET :offset";
            params.addValue("limit", c.size()).addValue("offset", (long) c.page() * c.size());
        }
        return new RoomPage(
            jdbcTemplate.query(sql, params, ROW_MAPPER), total == null ? 0 : total, c.page(), c.size());
    }

    @Override
    public Optional<Room> findById(UUID id) {
        String sql = "SELECT * FROM rooms WHERE id = :id";
        List<Room> list = jdbcTemplate.query(sql, Map.of("id", id), ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public List<Room> findByPropertyId(UUID propertyId) {
        String sql = "SELECT * FROM rooms WHERE property_id = :propertyId AND archived_at IS NULL ORDER BY room_number ASC";
        return jdbcTemplate.query(sql, Map.of("propertyId", propertyId), ROW_MAPPER);
    }

    @Override
    public List<Room> findByPropertyIdAndStatus(UUID propertyId, RoomStatus status) {
        String sql = "SELECT * FROM rooms WHERE property_id = :propertyId AND status = :status AND archived_at IS NULL ORDER BY room_number ASC";
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
