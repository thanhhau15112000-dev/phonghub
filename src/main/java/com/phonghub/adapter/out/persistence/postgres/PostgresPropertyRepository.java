package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
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

public class PostgresPropertyRepository implements PropertyRepositoryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresPropertyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Property> ROW_MAPPER = (rs, rowNum) -> {
        String ownerIdStr = rs.getString("owner_id");
        UUID ownerId = ownerIdStr != null ? UUID.fromString(ownerIdStr) : null;
        String statusStr = rs.getString("approval_status");
        PropertyApprovalStatus status = statusStr != null ? PropertyApprovalStatus.valueOf(statusStr) : PropertyApprovalStatus.APPROVED;
        String rejectionReason = rs.getString("rejection_reason");

        return new Property(
            UUID.fromString(rs.getString("id")),
            rs.getString("name"),
            rs.getString("address"),
            rs.getString("description"),
            rs.getInt("total_rooms"),
            ownerId,
            status,
            rejectionReason,
            toInstant(rs.getTimestamp("created_at"))
        );
    };

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }

    @Override
    public Property save(Property property) {
        String sql = """
            INSERT INTO properties (id, name, address, description, total_rooms, owner_id, approval_status, rejection_reason, created_at, updated_at)
            VALUES (:id, :name, :address, :description, :totalRooms, :ownerId, :approvalStatus, :rejectionReason, :createdAt, :updatedAt)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                address = EXCLUDED.address,
                description = EXCLUDED.description,
                total_rooms = EXCLUDED.total_rooms,
                owner_id = EXCLUDED.owner_id,
                approval_status = EXCLUDED.approval_status,
                rejection_reason = EXCLUDED.rejection_reason,
                updated_at = EXCLUDED.updated_at
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", property.id())
            .addValue("name", property.name())
            .addValue("address", property.address())
            .addValue("description", property.description())
            .addValue("totalRooms", property.totalRooms())
            .addValue("ownerId", property.ownerId())
            .addValue("approvalStatus", property.approvalStatus() != null ? property.approvalStatus().name() : PropertyApprovalStatus.APPROVED.name())
            .addValue("rejectionReason", property.rejectionReason())
            .addValue("createdAt", Timestamp.from(property.createdAt()))
            .addValue("updatedAt", Timestamp.from(Instant.now()));

        jdbcTemplate.update(sql, params);
        return property;
    }

    @Override
    public Optional<Property> findById(UUID id) {
        String sql = "SELECT id, name, address, description, total_rooms, owner_id, approval_status, rejection_reason, created_at FROM properties WHERE id = :id";
        List<Property> list = jdbcTemplate.query(sql, Map.of("id", id), ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public List<Property> findAll() {
        String sql = "SELECT id, name, address, description, total_rooms, owner_id, approval_status, rejection_reason, created_at FROM properties ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    @Override
    public List<Property> findAllById(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT id, name, address, description, total_rooms, owner_id, approval_status, rejection_reason, created_at FROM properties WHERE id IN (:ids) ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, Map.of("ids", ids), ROW_MAPPER);
    }

    @Override
    public List<Property> findByOwnerId(UUID ownerId) {
        if (ownerId == null) {
            return Collections.emptyList();
        }
        String sql = "SELECT id, name, address, description, total_rooms, owner_id, approval_status, rejection_reason, created_at FROM properties WHERE owner_id = :ownerId ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, Map.of("ownerId", ownerId), ROW_MAPPER);
    }

    @Override
    public boolean existsById(UUID id) {
        String sql = "SELECT COUNT(*) FROM properties WHERE id = :id";
        Integer count = jdbcTemplate.queryForObject(sql, Map.of("id", id), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM properties WHERE id = :id";
        jdbcTemplate.update(sql, Map.of("id", id));
    }
}
