package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.RoomConditionRepositoryPort;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.RoomConditionItem;
import com.phonghub.domain.model.RoomConditionSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresRoomConditionRepository implements RoomConditionRepositoryPort {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresRoomConditionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static RoomConditionSnapshot mapSnapshot(ResultSet rs, int row) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new RoomConditionSnapshot(rs.getObject("id", UUID.class), rs.getObject("room_id", UUID.class),
            rs.getBigDecimal("electric_meter"), rs.getBigDecimal("water_meter"), rs.getString("condition_note"),
            rs.getString("electric_meter_photo_key"), rs.getString("water_meter_photo_key"),
            created == null ? Instant.now() : created.toInstant(), rs.getObject("created_by", UUID.class));
    }

    private static RoomConditionItem mapItem(ResultSet rs, int row) throws SQLException {
        return new RoomConditionItem(rs.getObject("id", UUID.class), rs.getObject("snapshot_id", UUID.class),
            rs.getObject("asset_id", UUID.class), rs.getInt("position"), rs.getString("asset_name"),
            rs.getInt("quantity"), AssetStatus.valueOf(rs.getString("status")), rs.getString("condition_note"),
            rs.getBigDecimal("estimated_value"));
    }

    @Override public RoomConditionSnapshot save(RoomConditionSnapshot snapshot) {
        jdbc.update("""
            INSERT INTO room_condition_snapshots (id, room_id, electric_meter, water_meter, condition_note,
                electric_meter_photo_key, water_meter_photo_key, created_at, created_by)
            VALUES (:id, :roomId, :electric, :water, :note, :electricPhoto, :waterPhoto, :createdAt, :createdBy)
            """, new MapSqlParameterSource().addValue("id", snapshot.id()).addValue("roomId", snapshot.roomId())
            .addValue("electric", snapshot.electricMeter()).addValue("water", snapshot.waterMeter())
            .addValue("note", snapshot.conditionNote()).addValue("electricPhoto", snapshot.electricMeterPhotoKey())
            .addValue("waterPhoto", snapshot.waterMeterPhotoKey()).addValue("createdAt", Timestamp.from(snapshot.createdAt()))
            .addValue("createdBy", snapshot.createdBy()));
        return snapshot;
    }

    @Override public Optional<RoomConditionSnapshot> findLatestByRoomId(UUID roomId) {
        return jdbc.query("SELECT * FROM room_condition_snapshots WHERE room_id = :roomId "
                + "ORDER BY created_at DESC, id DESC LIMIT 1", Map.of("roomId", roomId), PostgresRoomConditionRepository::mapSnapshot)
            .stream().findFirst();
    }

    @Override public List<RoomConditionItem> findItemsBySnapshotId(UUID snapshotId) {
        return jdbc.query("SELECT * FROM room_condition_items WHERE snapshot_id = :snapshotId ORDER BY position",
            Map.of("snapshotId", snapshotId), PostgresRoomConditionRepository::mapItem);
    }

    @Override public void saveItems(UUID snapshotId, List<RoomConditionItem> items) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM room_condition_snapshots WHERE id = :snapshotId",
            Map.of("snapshotId", snapshotId), Integer.class) == 0) {
            throw new IllegalArgumentException("Không tìm thấy biên bản hiện trạng.");
        }
        if (items.isEmpty()) return;
        String sql = """
            INSERT INTO room_condition_items (id, snapshot_id, asset_id, position, asset_name, quantity, status,
                condition_note, estimated_value)
            VALUES (:id, :snapshotId, :assetId, :position, :name, :quantity, :status, :note, :value)
            """;
        for (RoomConditionItem item : items) {
            jdbc.update(sql, new MapSqlParameterSource().addValue("id", item.id()).addValue("snapshotId", snapshotId)
                .addValue("assetId", item.assetId()).addValue("position", item.position()).addValue("name", item.name())
                .addValue("quantity", item.quantity()).addValue("status", item.status().name())
                .addValue("note", item.note()).addValue("value", item.estimatedValue()));
        }
    }
}
