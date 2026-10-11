package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.RoomAssetRepositoryPort;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.RoomAsset;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresRoomAssetRepository implements RoomAssetRepositoryPort {

    private static final RowMapper<RoomAsset> ROW_MAPPER = (rs, rowNum) -> new RoomAsset(
        rs.getObject("id", UUID.class),
        rs.getObject("room_id", UUID.class),
        rs.getString("asset_code"),
        rs.getString("name"),
        rs.getString("serial_number"),
        rs.getInt("quantity"),
        AssetStatus.valueOf(rs.getString("status")),
        rs.getString("condition"),
        rs.getString("image_url"),
        rs.getBigDecimal("estimated_value"),
        toInstant(rs.getTimestamp("deleted_at")),
        toInstant(rs.getTimestamp("created_at")),
        rs.getObject("created_by", UUID.class),
        toInstant(rs.getTimestamp("updated_at")),
        rs.getObject("updated_by", UUID.class)
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresRoomAssetRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    @Override
    public RoomAsset save(RoomAsset asset) {
        String sql = """
            INSERT INTO room_assets (id, room_id, asset_code, name, serial_number, quantity, status, condition,
                image_url, estimated_value, deleted_at, created_at, created_by, updated_at, updated_by)
            VALUES (:id, :roomId, :assetCode, :name, :serialNumber, :quantity, :status, :condition,
                :imageUrl, :estimatedValue, :deletedAt, :createdAt, :createdBy, :updatedAt, :updatedBy)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                serial_number = EXCLUDED.serial_number,
                quantity = EXCLUDED.quantity,
                status = EXCLUDED.status,
                condition = EXCLUDED.condition,
                image_url = EXCLUDED.image_url,
                estimated_value = EXCLUDED.estimated_value,
                deleted_at = EXCLUDED.deleted_at,
                updated_at = EXCLUDED.updated_at,
                updated_by = EXCLUDED.updated_by
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", asset.id())
            .addValue("roomId", asset.roomId())
            .addValue("assetCode", asset.assetCode())
            .addValue("name", asset.name())
            .addValue("serialNumber", asset.serialNumber())
            .addValue("quantity", asset.quantity())
            .addValue("status", asset.status().name())
            .addValue("condition", asset.note() != null ? asset.note() : "")
            .addValue("imageUrl", asset.imageUrl())
            .addValue("estimatedValue", asset.estimatedValue())
            .addValue("deletedAt", toTimestamp(asset.deletedAt()))
            .addValue("createdAt", toTimestamp(asset.createdAt()))
            .addValue("createdBy", asset.createdBy())
            .addValue("updatedAt", toTimestamp(asset.updatedAt()))
            .addValue("updatedBy", asset.updatedBy());
        jdbcTemplate.update(sql, params);
        return asset;
    }

    @Override
    public Optional<RoomAsset> findById(UUID id) {
        List<RoomAsset> rows = jdbcTemplate.query("SELECT * FROM room_assets WHERE id = :id", Map.of("id", id), ROW_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<RoomAsset> findActiveByRoomId(UUID roomId) {
        return jdbcTemplate.query(
            "SELECT * FROM room_assets WHERE room_id = :roomId AND deleted_at IS NULL ORDER BY created_at, name",
            Map.of("roomId", roomId), ROW_MAPPER);
    }

    @Override
    public boolean existsByAssetCode(String assetCode) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM room_assets WHERE lower(asset_code) = lower(:code)",
            Map.of("code", assetCode), Integer.class);
        return count != null && count > 0;
    }
}
