package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.TenantDataProtectionPort;
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
    private final TenantDataProtectionPort dataProtection;

    public PostgresTenantRepository(
        NamedParameterJdbcTemplate jdbcTemplate,
        TenantDataProtectionPort dataProtection
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataProtection = dataProtection;
    }

    private Tenant mapTenant(ResultSet rs, int rowNum) throws SQLException {
        String userIdStr = rs.getString("user_id");
        UUID userId = userIdStr != null ? UUID.fromString(userIdStr) : null;
        String identityCardNumber = dataProtection.reveal(new TenantDataProtectionPort.ProtectedValue(
            rs.getString("identity_card_ciphertext"),
            rs.getString("identity_card_nonce"),
            rs.getString("identity_card_hmac"),
            rs.getString("identity_card_key_id")
        ));
        return new Tenant(
            UUID.fromString(rs.getString("id")),
            userId,
            rs.getString("full_name"),
            identityCardNumber,
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("permanent_address"),
            toInstant(rs.getTimestamp("created_at"))
        );
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }

    @Override
    public Tenant save(Tenant tenant) {
        TenantDataProtectionPort.ProtectedValue identity = dataProtection.protect(tenant.identityCardNumber());
        String sql = """
            INSERT INTO tenants (
                id, user_id, full_name, identity_card_ciphertext, identity_card_nonce,
                identity_card_hmac, identity_card_key_id, phone, email, permanent_address, created_at
            )
            VALUES (
                :id, :userId, :fullName, :identityCardCiphertext, :identityCardNonce,
                :identityCardHmac, :identityCardKeyId, :phone, :email, :permanentAddress, :createdAt
            )
            ON CONFLICT (id) DO UPDATE SET
                user_id = EXCLUDED.user_id,
                full_name = EXCLUDED.full_name,
                identity_card_ciphertext = EXCLUDED.identity_card_ciphertext,
                identity_card_nonce = EXCLUDED.identity_card_nonce,
                identity_card_hmac = EXCLUDED.identity_card_hmac,
                identity_card_key_id = EXCLUDED.identity_card_key_id,
                phone = EXCLUDED.phone,
                email = EXCLUDED.email,
                permanent_address = EXCLUDED.permanent_address
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", tenant.id())
            .addValue("userId", tenant.userId())
            .addValue("fullName", tenant.fullName())
            .addValue("identityCardCiphertext", identity.ciphertext())
            .addValue("identityCardNonce", identity.nonce())
            .addValue("identityCardHmac", identity.fingerprint())
            .addValue("identityCardKeyId", identity.keyId())
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
        List<Tenant> list = jdbcTemplate.query(sql, Map.of("id", id), this::mapTenant);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public Optional<Tenant> findByUserId(UUID userId) {
        if (userId == null) return Optional.empty();
        String sql = "SELECT * FROM tenants WHERE user_id = :userId";
        List<Tenant> list = jdbcTemplate.query(sql, Map.of("userId", userId), this::mapTenant);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public Optional<Tenant> findByIdForUpdate(UUID id) {
        List<Tenant> tenants = jdbcTemplate.query(
            "SELECT * FROM tenants WHERE id = :id FOR UPDATE", Map.of("id", id), this::mapTenant);
        return tenants.stream().findFirst();
    }

    @Override
    public Optional<Tenant> findByIdentityCardNumber(String idCard) {
        if (idCard == null) return Optional.empty();
        String sql = "SELECT * FROM tenants WHERE identity_card_hmac = :idCardHmac";
        List<Tenant> list = jdbcTemplate.query(
            sql,
            Map.of("idCardHmac", dataProtection.fingerprint(idCard)),
            this::mapTenant
        );
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public List<Tenant> findAll() {
        String sql = "SELECT * FROM tenants ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, this::mapTenant);
    }
}
