package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
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

public class PostgresUserRepository implements UserRepositoryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresUserRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<User> ROW_MAPPER = (rs, rowNum) -> new User(
        UUID.fromString(rs.getString("id")),
        rs.getString("email"),
        rs.getString("full_name"),
        rs.getString("phone"),
        UserRole.valueOf(rs.getString("role")),
        User.UserStatus.valueOf(rs.getString("status")),
        toInstant(rs.getTimestamp("created_at"))
    );

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }

    @Override
    public User save(User user) {
        String sql = """
            INSERT INTO users (id, email, full_name, phone, role, status, created_at, updated_at)
            VALUES (:id, :email, :fullName, :phone, :role, :status, :createdAt, :updatedAt)
            ON CONFLICT (id) DO UPDATE SET
                email = EXCLUDED.email,
                full_name = EXCLUDED.full_name,
                phone = EXCLUDED.phone,
                role = EXCLUDED.role,
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", user.id())
            .addValue("email", user.email())
            .addValue("fullName", user.fullName())
            .addValue("phone", user.phone())
            .addValue("role", user.role().name())
            .addValue("status", user.status().name())
            .addValue("createdAt", Timestamp.from(user.createdAt()))
            .addValue("updatedAt", Timestamp.from(Instant.now()));

        jdbcTemplate.update(sql, params);
        return user;
    }

    @Override
    public Optional<User> findById(UUID id) {
        String sql = "SELECT * FROM users WHERE id = :id";
        List<User> list = jdbcTemplate.query(sql, Map.of("id", id), ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        String sql = "SELECT * FROM users WHERE LOWER(email) = LOWER(:email)";
        List<User> list = jdbcTemplate.query(sql, Map.of("email", email.trim()), ROW_MAPPER);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public List<User> findByRole(UserRole role) {
        String sql = "SELECT * FROM users WHERE role = :role";
        return jdbcTemplate.query(sql, Map.of("role", role.name()), ROW_MAPPER);
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }
}
