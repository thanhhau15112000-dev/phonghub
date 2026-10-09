package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.HandoverItemRepositoryPort;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.HandoverItem;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresHandoverItemRepository implements HandoverItemRepositoryPort {

    private static final RowMapper<HandoverItem> ROW_MAPPER = (rs, rowNum) -> new HandoverItem(
        rs.getObject("id", UUID.class),
        rs.getObject("handover_id", UUID.class),
        rs.getObject("asset_id", UUID.class),
        rs.getString("asset_name"),
        rs.getInt("quantity"),
        AssetStatus.valueOf(rs.getString("status")),
        rs.getString("condition_note")
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresHandoverItemRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void replaceAll(UUID handoverId, List<HandoverItem> items, UUID actorId) {
        jdbcTemplate.update("DELETE FROM handover_items WHERE handover_id = :handoverId",
            Map.of("handoverId", handoverId));
        for (HandoverItem item : items) {
            jdbcTemplate.update("""
                INSERT INTO handover_items (id, handover_id, asset_id, asset_name, quantity, status, condition_note,
                    created_by, updated_by)
                VALUES (:id, :handoverId, :assetId, :name, :quantity, :status, :note, :actor, :actor)
                """, new MapSqlParameterSource()
                .addValue("id", item.id())
                .addValue("handoverId", handoverId)
                .addValue("assetId", item.assetId())
                .addValue("name", item.assetName())
                .addValue("quantity", item.quantity())
                .addValue("status", item.status().name())
                .addValue("note", item.conditionNote())
                .addValue("actor", actorId));
        }
    }

    @Override
    public List<HandoverItem> findByHandoverId(UUID handoverId) {
        return jdbcTemplate.query("SELECT * FROM handover_items WHERE handover_id = :handoverId ORDER BY asset_name",
            Map.of("handoverId", handoverId), ROW_MAPPER);
    }
}
