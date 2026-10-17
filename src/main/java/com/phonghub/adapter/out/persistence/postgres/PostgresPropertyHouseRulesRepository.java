package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.PropertyHouseRulesRepositoryPort;
import com.phonghub.domain.model.PropertyHouseRules;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresPropertyHouseRulesRepository implements PropertyHouseRulesRepositoryPort {

    private static final RowMapper<PropertyHouseRules> ROW_MAPPER = (rs, rowNum) -> new PropertyHouseRules(
        rs.getObject("property_id", UUID.class),
        rs.getString("content"),
        rs.getInt("version"),
        rs.getTimestamp("updated_at").toInstant(),
        rs.getObject("updated_by", UUID.class)
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresPropertyHouseRulesRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PropertyHouseRules> findByPropertyId(UUID propertyId) {
        List<PropertyHouseRules> rows = jdbcTemplate.query(
            "SELECT * FROM property_house_rules WHERE property_id = :propertyId",
            Map.of("propertyId", propertyId), ROW_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public PropertyHouseRules save(PropertyHouseRules rules) {
        jdbcTemplate.update("""
            INSERT INTO property_house_rules (property_id, content, version, created_by, updated_at, updated_by)
            VALUES (:propertyId, :content, :version, :actor, :updatedAt, :actor)
            ON CONFLICT (property_id) DO UPDATE SET
                content = EXCLUDED.content,
                version = EXCLUDED.version,
                updated_at = EXCLUDED.updated_at,
                updated_by = EXCLUDED.updated_by
            """, new MapSqlParameterSource()
            .addValue("propertyId", rules.propertyId())
            .addValue("content", rules.content())
            .addValue("version", rules.version())
            .addValue("updatedAt", Timestamp.from(rules.updatedAt()))
            .addValue("actor", rules.updatedBy()));
        return rules;
    }
}
