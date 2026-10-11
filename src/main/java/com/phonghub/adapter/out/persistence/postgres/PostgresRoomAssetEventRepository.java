package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.RoomAssetEventRepositoryPort;
import com.phonghub.domain.model.AssetEventType;
import com.phonghub.domain.model.RoomAssetEvent;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresRoomAssetEventRepository implements RoomAssetEventRepositoryPort {

    private static final RowMapper<RoomAssetEvent> ROW_MAPPER = (rs, rowNum) -> new RoomAssetEvent(
        rs.getObject("id", UUID.class),
        rs.getObject("asset_id", UUID.class),
        AssetEventType.valueOf(rs.getString("event_type")),
        rs.getString("description"),
        rs.getBigDecimal("cost"),
        rs.getObject("maintenance_ticket_id", UUID.class),
        rs.getTimestamp("created_at").toInstant(),
        rs.getObject("created_by", UUID.class)
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresRoomAssetEventRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RoomAssetEvent save(RoomAssetEvent event) {
        String sql = """
            INSERT INTO room_asset_events (id, asset_id, event_type, description, cost, maintenance_ticket_id,
                created_at, created_by, updated_at, updated_by)
            VALUES (:id, :assetId, :type, :description, :cost, :ticketId, :createdAt, :createdBy, :createdAt, :createdBy)
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", event.id())
            .addValue("assetId", event.assetId())
            .addValue("type", event.type().name())
            .addValue("description", event.description())
            .addValue("cost", event.cost())
            .addValue("ticketId", event.maintenanceTicketId())
            .addValue("createdAt", Timestamp.from(event.createdAt()))
            .addValue("createdBy", event.createdBy());
        jdbcTemplate.update(sql, params);
        return event;
    }

    @Override
    public List<RoomAssetEvent> findByAssetId(UUID assetId) {
        return jdbcTemplate.query(
            "SELECT * FROM room_asset_events WHERE asset_id = :assetId ORDER BY created_at DESC",
            Map.of("assetId", assetId), ROW_MAPPER);
    }
}
