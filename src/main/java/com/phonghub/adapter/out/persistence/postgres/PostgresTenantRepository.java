package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.model.Tenant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresTenantRepository implements TenantRepositoryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresTenantRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Tenant> ROW_MAPPER = (rs, rowNum) -> {
        String userIdStr = rs.getString("user_id");
        UUID userId = userIdStr != null ? UUID.fromString(userIdStr) : null;
        return new Tenant(
            UUID.fromString(rs.getString("id")),
            userId,
            rs.getString("full_name"),
            rs.getString("identity_card_number"),
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("permanent_address"),
            toInstant(rs.getTimestamp("created_at"))
        );
    };

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }

    @Override
    public Tenant save(Tenant tenant) {
        String sql = """
            INSERT INTO tenants (id, user_id, full_name, identity_card_number, phone, email, permanent_address, created_at)
            VALUES (:id, :userId, :fullName, :identityCardNumber, :phone, :email, :permanentAddress, :createdAt)
            ON CONFLICT (id) DO UPDATE SET
                user_id = EXCLUDED.user_id,
                full_name = EXCLUDED.full_name,
                identity_card_number = EXCLUDED.identity_card_number,
                phone = EXCLUDED.phone,
                email = EXCLUDED.email,
                permanent_address = EXCLUDED.permanent_address
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", tenant.id())
            .addValue("userId", tenant.userId())
            .addValue("fullName", tenant.fullName())
            .addValue("identityCardNumber", tenant.identityCardNumber())
            .addValue("phone", tenant.phone())
            .addValue("email", tenant.email())
            .addValue("permanentAddress", tenant.permanentAddress())
            .addValue("createdAt", Timestamp.from(tenant.createdAt()));

        jdbcTemplate.update(sql, params);
        return tenant;
    }

    @Override
    public Optional<Tenant> findById(UUID id) {
        String sql = "SELECT * FROM tenants WHERE id = :id";
        List<Tenant> list = jdbcTemplate.query(sql, Map.of("id", id), ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public Optional<Tenant> findByUserId(UUID userId) {
        if (userId == null) return Optional.empty();
        String sql = "SELECT * FROM tenants WHERE user_id = :userId";
        List<Tenant> list = jdbcTemplate.query(sql, Map.of("userId", userId), ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public Optional<Tenant> findByIdentityCardNumber(String idCard) {
        if (idCard == null) return Optional.empty();
        String sql = "SELECT * FROM tenants WHERE LOWER(identity_card_number) = LOWER(:idCard)";
        List<Tenant> list = jdbcTemplate.query(sql, Map.of("idCard", idCard.trim()), ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public List<Tenant> findAll() {
        String sql = "SELECT * FROM tenants ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }
}
