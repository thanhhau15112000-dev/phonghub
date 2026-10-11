package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.adapter.out.security.PersonalDataFields;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.TenantDataProtectionPort;
import com.phonghub.domain.model.Tenant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
            dataProtection.decryptField(PersonalDataFields.TENANTS_FULL_NAME, rs.getString("full_name")),
            identityCardNumber,
            dataProtection.decryptField(PersonalDataFields.TENANTS_PHONE, rs.getString("phone")),
            dataProtection.decryptField(PersonalDataFields.TENANTS_EMAIL, rs.getString("email")),
            dataProtection.decryptField(PersonalDataFields.TENANTS_ADDRESS, rs.getString("permanent_address")),
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
                identity_card_hmac, identity_card_key_id, phone, phone_hmac, email, permanent_address, created_at
            )
            VALUES (
                :id, :userId, :fullName, :identityCardCiphertext, :identityCardNonce,
                :identityCardHmac, :identityCardKeyId, :phone, :phoneHmac, :email, :permanentAddress, :createdAt
            )
            ON CONFLICT (id) DO UPDATE SET
                user_id = EXCLUDED.user_id,
                full_name = EXCLUDED.full_name,
                identity_card_ciphertext = EXCLUDED.identity_card_ciphertext,
                identity_card_nonce = EXCLUDED.identity_card_nonce,
                identity_card_hmac = EXCLUDED.identity_card_hmac,
                identity_card_key_id = EXCLUDED.identity_card_key_id,
                phone = EXCLUDED.phone,
                phone_hmac = EXCLUDED.phone_hmac,
                email = EXCLUDED.email,
                permanent_address = EXCLUDED.permanent_address
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", tenant.id())
            .addValue("userId", tenant.userId())
            .addValue("fullName", dataProtection.encryptField(PersonalDataFields.TENANTS_FULL_NAME, tenant.fullName()))
            .addValue("identityCardCiphertext", identity.ciphertext())
            .addValue("identityCardNonce", identity.nonce())
            .addValue("identityCardHmac", identity.fingerprint())
            .addValue("identityCardKeyId", identity.keyId())
            .addValue("phone", dataProtection.encryptField(PersonalDataFields.TENANTS_PHONE, tenant.phone()))
            .addValue("phoneHmac", dataProtection.blindIndex(PersonalDataFields.TENANTS_PHONE,
                PersonalDataFields.normalizePhone(tenant.phone())))
            .addValue("email", dataProtection.encryptField(PersonalDataFields.TENANTS_EMAIL, tenant.email()))
            .addValue("permanentAddress", dataProtection.encryptField(PersonalDataFields.TENANTS_ADDRESS,
                tenant.permanentAddress()))
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
    public void linkToProperty(UUID tenantId, UUID propertyId, UUID actorId) {
        jdbcTemplate.update("""
            INSERT INTO tenant_properties (tenant_id, property_id, created_by)
            VALUES (:tenantId, :propertyId, :actorId)
            ON CONFLICT (tenant_id, property_id) DO NOTHING
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
            .addValue("propertyId", propertyId).addValue("actorId", actorId));
    }

    @Override
    public Set<UUID> findLinkedPropertyIds(UUID tenantId) {
        return new HashSet<>(jdbcTemplate.query(
            "SELECT property_id FROM tenant_properties WHERE tenant_id = :tenantId",
            Map.of("tenantId", tenantId), (rs, rowNum) -> rs.getObject("property_id", UUID.class)));
    }

    @Override
    public List<PropertyLink> findLinks(UUID propertyId) {
        return jdbcTemplate.query(
            "SELECT tenant_id, hidden_at FROM tenant_properties WHERE property_id = :propertyId",
            Map.of("propertyId", propertyId),
            (rs, rowNum) -> new PropertyLink(rs.getObject("tenant_id", UUID.class),
                toNullableInstant(rs.getTimestamp("hidden_at"))));
    }

    @Override
    public void setHidden(UUID tenantId, UUID propertyId, Instant hiddenAt, UUID actorId) {
        jdbcTemplate.update("""
            INSERT INTO tenant_properties (tenant_id, property_id, created_by, hidden_at, hidden_by)
            VALUES (:tenantId, :propertyId, :actorId, :hiddenAt, :hiddenBy)
            ON CONFLICT (tenant_id, property_id) DO UPDATE SET
                hidden_at = EXCLUDED.hidden_at,
                hidden_by = EXCLUDED.hidden_by
            """, new MapSqlParameterSource().addValue("tenantId", tenantId)
            .addValue("propertyId", propertyId).addValue("actorId", actorId)
            .addValue("hiddenAt", hiddenAt == null ? null : Timestamp.from(hiddenAt))
            .addValue("hiddenBy", hiddenAt == null ? null : actorId));
    }

    @Override
    public List<Tenant> findAllByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query("SELECT * FROM tenants WHERE id IN (:ids)", Map.of("ids", ids), this::mapTenant);
    }

    private static Instant toNullableInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    @Override
    public List<Tenant> findAll() {
        String sql = "SELECT * FROM tenants ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, this::mapTenant);
    }
}
