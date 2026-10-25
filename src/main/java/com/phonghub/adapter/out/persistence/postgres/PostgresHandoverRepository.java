package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.HandoverRepositoryPort;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverStatus;
import com.phonghub.domain.model.HandoverType;
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

public class PostgresHandoverRepository implements HandoverRepositoryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresHandoverRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    private Handover map(ResultSet rs, int rowNum) throws SQLException {
        return new Handover(
            rs.getObject("id", UUID.class),
            rs.getObject("contract_id", UUID.class),
            HandoverType.valueOf(rs.getString("type")),
            HandoverStatus.valueOf(rs.getString("status")),
            rs.getBigDecimal("initial_electric_meter"),
            rs.getBigDecimal("initial_water_meter"),
            rs.getString("room_condition"),
            rs.getString("keys_provided"),
            trimmed(rs.getString("contract_terms_hash")),
            rs.getString("house_rules_text"),
            trimmed(rs.getString("house_rules_hash")),
            rs.getString("rejection_reason"),
            instant(rs.getTimestamp("sent_at")),
            rs.getBoolean("is_confirmed_by_tenant"),
            instant(rs.getTimestamp("confirmed_at")),
            rs.getObject("approved_by", UUID.class),
            instant(rs.getTimestamp("approved_at")),
            rs.getBigDecimal("final_deduction"),
            instant(rs.getTimestamp("created_at")),
            rs.getObject("created_by", UUID.class),
            instant(rs.getTimestamp("updated_at")),
            rs.getObject("updated_by", UUID.class)
        );
    }

    @Override
    public Handover save(Handover handover) {
        String sql = """
            INSERT INTO handovers (id, contract_id, type, status, initial_electric_meter, initial_water_meter,
                room_condition, keys_provided, contract_terms_hash, house_rules_text, house_rules_hash,
                rejection_reason, sent_at, is_confirmed_by_tenant, confirmed_at, approved_by, approved_at,
                final_deduction, created_at, created_by, updated_at, updated_by)
            VALUES (:id, :contractId, :type, :status, :electric, :water, :roomCondition, :keys, :termsHash,
                :rulesText, :rulesHash, :reason, :sentAt, :confirmed, :confirmedAt, :approvedBy, :approvedAt,
                :finalDeduction, :createdAt, :createdBy, :updatedAt, :updatedBy)
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                initial_electric_meter = EXCLUDED.initial_electric_meter,
                initial_water_meter = EXCLUDED.initial_water_meter,
                room_condition = EXCLUDED.room_condition,
                keys_provided = EXCLUDED.keys_provided,
                contract_terms_hash = EXCLUDED.contract_terms_hash,
                house_rules_text = EXCLUDED.house_rules_text,
                house_rules_hash = EXCLUDED.house_rules_hash,
                rejection_reason = EXCLUDED.rejection_reason,
                sent_at = EXCLUDED.sent_at,
                is_confirmed_by_tenant = EXCLUDED.is_confirmed_by_tenant,
                confirmed_at = EXCLUDED.confirmed_at,
                approved_by = EXCLUDED.approved_by,
                approved_at = EXCLUDED.approved_at,
                final_deduction = EXCLUDED.final_deduction,
                updated_at = EXCLUDED.updated_at,
                updated_by = EXCLUDED.updated_by
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", handover.id())
            .addValue("contractId", handover.contractId())
            .addValue("type", handover.type().name())
            .addValue("status", handover.status().name())
            .addValue("electric", handover.initialElectricMeter())
            .addValue("water", handover.initialWaterMeter())
            .addValue("roomCondition", handover.roomCondition())
            .addValue("keys", handover.keysProvided())
            .addValue("termsHash", handover.contractTermsHash())
            .addValue("rulesText", handover.houseRulesText())
            .addValue("rulesHash", handover.houseRulesHash())
            .addValue("reason", handover.rejectionReason())
            .addValue("sentAt", timestamp(handover.sentAt()))
            .addValue("confirmed", handover.confirmedByTenant())
            .addValue("confirmedAt", timestamp(handover.confirmedAt()))
            .addValue("approvedBy", handover.approvedBy())
            .addValue("approvedAt", timestamp(handover.approvedAt()))
            .addValue("finalDeduction", handover.finalDeduction())
            .addValue("createdAt", timestamp(handover.createdAt()))
            .addValue("createdBy", handover.createdBy())
            .addValue("updatedAt", timestamp(handover.updatedAt()))
            .addValue("updatedBy", handover.updatedBy());
        jdbcTemplate.update(sql, params);
        return handover;
    }

    @Override
    public void delete(UUID handoverId) {
        jdbcTemplate.update("DELETE FROM handovers WHERE id = :id", Map.of("id", handoverId));
    }

    @Override
    public Optional<Handover> find(UUID contractId, HandoverType type) {
        List<Handover> rows = jdbcTemplate.query(
            "SELECT * FROM handovers WHERE contract_id = :contractId AND type = :type",
            Map.of("contractId", contractId, "type", type.name()), this::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<Handover> findForUpdate(UUID contractId, HandoverType type) {
        List<Handover> rows = jdbcTemplate.query(
            "SELECT * FROM handovers WHERE contract_id = :contractId AND type = :type FOR UPDATE",
            Map.of("contractId", contractId, "type", type.name()), this::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }
}
