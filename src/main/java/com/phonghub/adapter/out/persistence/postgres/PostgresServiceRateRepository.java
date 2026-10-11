package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.ServiceRateRepositoryPort;
import com.phonghub.domain.model.ChargeType;
import com.phonghub.domain.model.ServiceRate;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresServiceRateRepository implements ServiceRateRepositoryPort {

    private static final RowMapper<ServiceRate> ROW_MAPPER = (rs, rowNum) -> new ServiceRate(
        rs.getObject("id", UUID.class),
        rs.getObject("property_id", UUID.class),
        rs.getString("service_name"),
        ChargeType.valueOf(rs.getString("charge_type")),
        rs.getBigDecimal("price_per_unit"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getObject("created_by", UUID.class),
        rs.getTimestamp("updated_at").toInstant(),
        rs.getObject("updated_by", UUID.class)
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresServiceRateRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ServiceRate save(ServiceRate rate) {
        jdbcTemplate.update("""
            INSERT INTO service_rates (id, property_id, service_name, charge_type, price_per_unit, created_at,
                created_by, updated_at, updated_by)
            VALUES (:id, :propertyId, :name, :type, :price, :createdAt, :createdBy, :updatedAt, :updatedBy)
            ON CONFLICT (id) DO UPDATE SET
                service_name = EXCLUDED.service_name,
                charge_type = EXCLUDED.charge_type,
                price_per_unit = EXCLUDED.price_per_unit,
                updated_at = EXCLUDED.updated_at,
                updated_by = EXCLUDED.updated_by
            """, new MapSqlParameterSource()
            .addValue("id", rate.id())
            .addValue("propertyId", rate.propertyId())
            .addValue("name", rate.serviceName())
            .addValue("type", rate.chargeType().name())
            .addValue("price", rate.pricePerUnit())
            .addValue("createdAt", Timestamp.from(rate.createdAt()))
            .addValue("createdBy", rate.createdBy())
            .addValue("updatedAt", Timestamp.from(rate.updatedAt()))
            .addValue("updatedBy", rate.updatedBy()));
        return rate;
    }

    @Override
    public Optional<ServiceRate> findById(UUID id) {
        List<ServiceRate> rows = jdbcTemplate.query("SELECT * FROM service_rates WHERE id = :id",
            Map.of("id", id), ROW_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<ServiceRate> findByPropertyId(UUID propertyId) {
        return jdbcTemplate.query("SELECT * FROM service_rates WHERE property_id = :propertyId ORDER BY service_name",
            Map.of("propertyId", propertyId), ROW_MAPPER);
    }

    @Override
    public Optional<ServiceRate> findByPropertyIdAndName(UUID propertyId, String serviceName) {
        List<ServiceRate> rows = jdbcTemplate.query("""
            SELECT * FROM service_rates WHERE property_id = :propertyId AND lower(service_name) = lower(:name)
            """, Map.of("propertyId", propertyId, "name", serviceName), ROW_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }
}
