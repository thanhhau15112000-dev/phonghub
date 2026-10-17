package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.TenantDocumentRepositoryPort;
import com.phonghub.domain.model.DocumentType;
import com.phonghub.domain.model.TenantDocument;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresTenantDocumentRepository implements TenantDocumentRepositoryPort {

    private static final RowMapper<TenantDocument> ROW_MAPPER = (rs, rowNum) -> new TenantDocument(
        rs.getObject("id", UUID.class),
        rs.getObject("tenant_id", UUID.class),
        rs.getObject("property_id", UUID.class),
        DocumentType.valueOf(rs.getString("document_type")),
        rs.getString("number_ciphertext"),
        rs.getString("number_nonce"),
        trimmed(rs.getString("number_hmac")),
        rs.getString("number_key_id"),
        rs.getString("number_last4"),
        rs.getString("front_image_url"),
        rs.getString("front_content_type"),
        rs.getString("back_image_url"),
        rs.getString("back_content_type"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getObject("created_by", UUID.class),
        rs.getTimestamp("updated_at").toInstant(),
        rs.getObject("updated_by", UUID.class)
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresTenantDocumentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    @Override
    public TenantDocument save(TenantDocument document) {
        String sql = """
            INSERT INTO tenant_documents (id, tenant_id, property_id, document_type, number_ciphertext, number_nonce,
                number_hmac, number_key_id, number_last4, front_image_url, front_content_type, back_image_url,
                back_content_type, created_at, created_by, updated_at, updated_by)
            VALUES (:id, :tenantId, :propertyId, :type, :ciphertext, :nonce, :hmac, :keyId, :last4, :frontKey,
                :frontType, :backKey, :backType, :createdAt, :createdBy, :updatedAt, :updatedBy)
            ON CONFLICT (tenant_id, document_type) DO UPDATE SET
                property_id = EXCLUDED.property_id,
                number_ciphertext = EXCLUDED.number_ciphertext,
                number_nonce = EXCLUDED.number_nonce,
                number_hmac = EXCLUDED.number_hmac,
                number_key_id = EXCLUDED.number_key_id,
                number_last4 = EXCLUDED.number_last4,
                front_image_url = EXCLUDED.front_image_url,
                front_content_type = EXCLUDED.front_content_type,
                back_image_url = EXCLUDED.back_image_url,
                back_content_type = EXCLUDED.back_content_type,
                updated_at = EXCLUDED.updated_at,
                updated_by = EXCLUDED.updated_by
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", document.id())
            .addValue("tenantId", document.tenantId())
            .addValue("propertyId", document.propertyId())
            .addValue("type", document.type().name())
            .addValue("ciphertext", document.numberCiphertext())
            .addValue("nonce", document.numberNonce())
            .addValue("hmac", document.numberHmac())
            .addValue("keyId", document.numberKeyId())
            .addValue("last4", document.numberLast4())
            .addValue("frontKey", document.frontImageKey())
            .addValue("frontType", document.frontContentType())
            .addValue("backKey", document.backImageKey())
            .addValue("backType", document.backContentType())
            .addValue("createdAt", Timestamp.from(document.createdAt()))
            .addValue("createdBy", document.createdBy())
            .addValue("updatedAt", Timestamp.from(document.updatedAt()))
            .addValue("updatedBy", document.updatedBy());
        jdbcTemplate.update(sql, params);
        return document;
    }

    @Override
    public List<TenantDocument> findByTenantId(UUID tenantId) {
        return jdbcTemplate.query("SELECT * FROM tenant_documents WHERE tenant_id = :tenantId ORDER BY document_type",
            Map.of("tenantId", tenantId), ROW_MAPPER);
    }

    @Override
    public Optional<TenantDocument> findByTenantIdAndType(UUID tenantId, DocumentType type) {
        List<TenantDocument> rows = jdbcTemplate.query(
            "SELECT * FROM tenant_documents WHERE tenant_id = :tenantId AND document_type = :type",
            Map.of("tenantId", tenantId, "type", type.name()), ROW_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public boolean existsByTypeAndNumberHmacForOtherTenant(DocumentType type, String numberHmac, UUID tenantId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM tenant_documents WHERE document_type = :type AND number_hmac = :hmac "
                + "AND tenant_id <> :tenantId",
            Map.of("type", type.name(), "hmac", numberHmac, "tenantId", tenantId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void delete(UUID tenantId, DocumentType type) {
        jdbcTemplate.update("DELETE FROM tenant_documents WHERE tenant_id = :tenantId AND document_type = :type",
            Map.of("tenantId", tenantId, "type", type.name()));
    }
}
