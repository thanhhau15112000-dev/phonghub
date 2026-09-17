package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.domain.model.StaffPropertyAssignment;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresStaffPropertyAssignmentRepository implements StaffPropertyAssignmentPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresStaffPropertyAssignmentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<StaffPropertyAssignment> ROW_MAPPER = (rs, rowNum) -> new StaffPropertyAssignment(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("staff_user_id")),
        UUID.fromString(rs.getString("property_id")),
        rs.getBoolean("can_collect_payment"),
        rs.getBoolean("can_manage_contracts"),
        toInstant(rs.getTimestamp("assigned_at"))
    );

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }

    @Override
    public StaffPropertyAssignment save(StaffPropertyAssignment assignment) {
        String sql = """
            INSERT INTO staff_property_assignments (id, staff_user_id, property_id, can_collect_payment, can_manage_contracts, assigned_at)
            VALUES (:id, :staffUserId, :propertyId, :canCollectPayment, :canManageContracts, :assignedAt)
            ON CONFLICT (staff_user_id, property_id) DO UPDATE SET
                can_collect_payment = EXCLUDED.can_collect_payment,
                can_manage_contracts = EXCLUDED.can_manage_contracts
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", assignment.id())
            .addValue("staffUserId", assignment.staffUserId())
            .addValue("propertyId", assignment.propertyId())
            .addValue("canCollectPayment", assignment.canCollectPayment())
            .addValue("canManageContracts", assignment.canManageContracts())
            .addValue("assignedAt", Timestamp.from(assignment.assignedAt()));

        jdbcTemplate.update(sql, params);
        return assignment;
    }

    @Override
    public Set<UUID> findPropertyIdsByUserId(UUID userId) {
        String sql = "SELECT property_id FROM staff_property_assignments WHERE staff_user_id = :userId";
        List<UUID> list = jdbcTemplate.query(sql, Map.of("userId", userId), (rs, rn) -> UUID.fromString(rs.getString("property_id")));
        return new HashSet<>(list);
    }

    @Override
    public List<StaffPropertyAssignment> findByUserId(UUID userId) {
        String sql = "SELECT * FROM staff_property_assignments WHERE staff_user_id = :userId";
        return jdbcTemplate.query(sql, Map.of("userId", userId), ROW_MAPPER);
    }

    @Override
    public List<StaffPropertyAssignment> findByPropertyId(UUID propertyId) {
        String sql = "SELECT * FROM staff_property_assignments WHERE property_id = :propertyId";
        return jdbcTemplate.query(sql, Map.of("propertyId", propertyId), ROW_MAPPER);
    }

    @Override
    public boolean isUserAssignedToProperty(UUID userId, UUID propertyId) {
        String sql = "SELECT COUNT(*) FROM staff_property_assignments WHERE staff_user_id = :userId AND property_id = :propertyId";
        Integer count = jdbcTemplate.queryForObject(sql, Map.of("userId", userId, "propertyId", propertyId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void deleteByUserIdAndPropertyId(UUID userId, UUID propertyId) {
        String sql = "DELETE FROM staff_property_assignments WHERE staff_user_id = :userId AND property_id = :propertyId";
        jdbcTemplate.update(sql, Map.of("userId", userId, "propertyId", propertyId));
    }
}
