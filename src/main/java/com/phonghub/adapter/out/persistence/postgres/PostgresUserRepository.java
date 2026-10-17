package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.adapter.out.security.PersonalDataFields;
import com.phonghub.application.port.out.TenantDataProtectionPort;
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
    private final TenantDataProtectionPort protector;
    private final RowMapper<User> rowMapper;

    public PostgresUserRepository(NamedParameterJdbcTemplate jdbcTemplate, TenantDataProtectionPort protector) {
        this.jdbcTemplate = jdbcTemplate;
        this.protector = protector;
        this.rowMapper = this::mapUser;
    }

    private User mapUser(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new User(
        UUID.fromString(rs.getString("id")),
        protector.decryptField(PersonalDataFields.USERS_USERNAME, rs.getString("username")),
        protector.decryptField(PersonalDataFields.USERS_EMAIL, rs.getString("email")),
        protector.decryptField(PersonalDataFields.USERS_FULL_NAME, rs.getString("full_name")),
        protector.decryptField(PersonalDataFields.USERS_PHONE, rs.getString("phone")),
        UserRole.valueOf(rs.getString("role")),
        User.UserStatus.valueOf(rs.getString("status")),
        rs.getBoolean("must_change_password"),
        toInstant(rs.getTimestamp("created_at"))
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }

    @Override
    public User save(User user) {
        String sql = """
            INSERT INTO users (id, username, username_hmac, email, email_hmac, full_name, phone, phone_hmac, role, status, must_change_password, created_at, updated_at)
            VALUES (:id, :username, :usernameHmac, :email, :emailHmac, :fullName, :phone, :phoneHmac, :role, :status, :mustChangePassword, :createdAt, :updatedAt)
            ON CONFLICT (id) DO UPDATE SET
                username = EXCLUDED.username,
                username_hmac = EXCLUDED.username_hmac,
                email = EXCLUDED.email,
                email_hmac = EXCLUDED.email_hmac,
                full_name = EXCLUDED.full_name,
                phone = EXCLUDED.phone,
                phone_hmac = EXCLUDED.phone_hmac,
                role = EXCLUDED.role,
                status = EXCLUDED.status,
                must_change_password = EXCLUDED.must_change_password,
                updated_at = EXCLUDED.updated_at
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", user.id())
            .addValue("username", protector.encryptField(PersonalDataFields.USERS_USERNAME, user.username()))
            .addValue("usernameHmac", protector.blindIndex(PersonalDataFields.USERS_USERNAME,
                PersonalDataFields.normalizeText(user.username())))
            .addValue("email", protector.encryptField(PersonalDataFields.USERS_EMAIL, user.email()))
            .addValue("emailHmac", protector.blindIndex(PersonalDataFields.USERS_EMAIL,
                PersonalDataFields.normalizeText(user.email())))
            .addValue("fullName", protector.encryptField(PersonalDataFields.USERS_FULL_NAME, user.fullName()))
            .addValue("phone", protector.encryptField(PersonalDataFields.USERS_PHONE, user.phone()))
            .addValue("phoneHmac", protector.blindIndex(PersonalDataFields.USERS_PHONE,
                PersonalDataFields.normalizePhone(user.phone())))
            .addValue("role", user.role().name())
            .addValue("status", user.status().name())
            .addValue("mustChangePassword", user.mustChangePassword())
            .addValue("createdAt", Timestamp.from(user.createdAt()))
            .addValue("updatedAt", Timestamp.from(Instant.now()));

        jdbcTemplate.update(sql, params);
        return user;
    }

    @Override
    public Optional<User> findById(UUID id) {
        String sql = "SELECT * FROM users WHERE id = :id";
        List<User> list = jdbcTemplate.query(sql, Map.of("id", id), rowMapper);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public Optional<User> findByUsername(String username) {
        String normalized = PersonalDataFields.normalizeText(username);
        if (normalized == null) return Optional.empty();
        String sql = "SELECT * FROM users WHERE username_hmac = :hmac";
        List<User> list = jdbcTemplate.query(sql,
            Map.of("hmac", protector.blindIndex(PersonalDataFields.USERS_USERNAME, normalized)), rowMapper);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String normalized = PersonalDataFields.normalizeText(email);
        if (normalized == null) return Optional.empty();
        String sql = "SELECT * FROM users WHERE email_hmac = :hmac";
        List<User> list = jdbcTemplate.query(sql,
            Map.of("hmac", protector.blindIndex(PersonalDataFields.USERS_EMAIL, normalized)), rowMapper);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public List<User> findByRole(UserRole role) {
        String sql = "SELECT * FROM users WHERE role = :role";
        return jdbcTemplate.query(sql, Map.of("role", role.name()), rowMapper);
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM users WHERE id = :id";
        jdbcTemplate.update(sql, Map.of("id", id));
    }
}
