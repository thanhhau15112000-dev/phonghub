package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.LateFeePolicyRepositoryPort;
import com.phonghub.domain.model.LateFeePolicy;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresLateFeePolicyRepository implements LateFeePolicyRepositoryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresLateFeePolicyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<LateFeePolicy> findByPropertyId(UUID propertyId) {
        List<LateFeePolicy> found = jdbcTemplate.query(
            "SELECT * FROM property_late_fee_policies WHERE property_id = :propertyId",
            Map.of("propertyId", propertyId),
            (rs, rowNum) -> {
                String updatedBy = rs.getString("updated_by");
                return new LateFeePolicy(
                    UUID.fromString(rs.getString("property_id")),
                    rs.getBoolean("enabled"),
                    LateFeePolicy.FeeType.valueOf(rs.getString("fee_type")),
                    rs.getBigDecimal("fee_value"),
                    rs.getInt("grace_days"),
                    rs.getBigDecimal("cap_amount"),
                    rs.getTimestamp("updated_at").toInstant(),
                    updatedBy != null ? UUID.fromString(updatedBy) : null
                );
            });
        return found.stream().findFirst();
    }

    @Override
    public LateFeePolicy save(LateFeePolicy policy) {
        jdbcTemplate.update("""
            INSERT INTO property_late_fee_policies
                (property_id, enabled, fee_type, fee_value, grace_days, cap_amount, updated_at, updated_by)
            VALUES (:propertyId, :enabled, :feeType, :feeValue, :graceDays, :capAmount, :updatedAt, :updatedBy)
            ON CONFLICT (property_id) DO UPDATE SET
                enabled = EXCLUDED.enabled,
                fee_type = EXCLUDED.fee_type,
                fee_value = EXCLUDED.fee_value,
                grace_days = EXCLUDED.grace_days,
                cap_amount = EXCLUDED.cap_amount,
                updated_at = EXCLUDED.updated_at,
                updated_by = EXCLUDED.updated_by
            """, new MapSqlParameterSource()
            .addValue("propertyId", policy.propertyId())
            .addValue("enabled", policy.enabled())
            .addValue("feeType", policy.feeType().name())
            .addValue("feeValue", policy.feeValue())
            .addValue("graceDays", policy.graceDays())
            .addValue("capAmount", policy.capAmount())
            .addValue("updatedAt", Timestamp.from(policy.updatedAt()))
            .addValue("updatedBy", policy.updatedBy()));
        return policy;
    }
}
