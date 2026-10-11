package com.phonghub.migration;

import com.phonghub.adapter.out.persistence.postgres.PostgresTenantDocumentRepository;
import com.phonghub.domain.model.DocumentType;
import com.phonghub.domain.model.TenantDocument;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class TenantDocumentPostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final String HMAC_A = "a".repeat(64);

    private String firstValue(Statement st, String sql) throws Exception {
        ResultSet rs = st.executeQuery(sql);
        rs.next();
        return rs.getString(1);
    }

    private TenantDocument gplx(UUID tenantId, UUID propertyId, UUID userId, String hmac, String frontKey) {
        Instant now = Instant.now();
        return new TenantDocument(UUID.randomUUID(), tenantId, propertyId, DocumentType.GPLX, "cipher", "nonce",
            hmac, "v1", "1234", frontKey, "image/png", null, null, now, userId, now, userId);
    }

    @Test
    @DisplayName("V16 stores encrypted documents, enforces one row per type, unique GPLX HMAC and required fields")
    void v16AndRepository() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds)
            .locations("classpath:db/migration", "classpath:db/seed/docker").load().migrate();

        UUID tenantA;
        UUID tenantB;
        UUID propertyId;
        UUID userId;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            tenantA = UUID.fromString(firstValue(st, "SELECT id FROM tenants ORDER BY id LIMIT 1"));
            tenantB = UUID.randomUUID();
            st.execute("INSERT INTO tenants (id, full_name, phone, identity_card_ciphertext, identity_card_nonce, "
                + "identity_card_hmac, identity_card_key_id) VALUES ('" + tenantB + "', 'B', '0900000077', 'c', 'n', '"
                + "b".repeat(64) + "', 'v1')");
            propertyId = UUID.fromString(firstValue(st, "SELECT id FROM properties LIMIT 1"));
            userId = UUID.fromString(firstValue(st, "SELECT id FROM users LIMIT 1"));
        }
        PostgresTenantDocumentRepository repository =
            new PostgresTenantDocumentRepository(new NamedParameterJdbcTemplate(ds));

        repository.save(gplx(tenantA, propertyId, userId, HMAC_A, "properties/p/tenant-documents/a.enc"));
        TenantDocument stored = repository.findByTenantIdAndType(tenantA, DocumentType.GPLX).orElseThrow();
        assertEquals(HMAC_A, stored.numberHmac());
        assertEquals("****1234", stored.maskedNumber());

        repository.save(gplx(tenantA, propertyId, userId, HMAC_A, "properties/p/tenant-documents/a2.enc"));
        assertEquals(1, repository.findByTenantId(tenantA).size(), "replacing keeps one row per tenant and type");
        assertEquals("properties/p/tenant-documents/a2.enc",
            repository.findByTenantIdAndType(tenantA, DocumentType.GPLX).orElseThrow().frontImageKey());

        assertTrue(repository.existsByTypeAndNumberHmacForOtherTenant(DocumentType.GPLX, HMAC_A, tenantB));
        assertFalse(repository.existsByTypeAndNumberHmacForOtherTenant(DocumentType.GPLX, HMAC_A, tenantA));
        UUID otherTenant = tenantB;
        assertThrows(Exception.class,
            () -> repository.save(gplx(otherTenant, propertyId, userId, HMAC_A, "properties/p/tenant-documents/b.enc")),
            "the same GPLX number must not be registered for two tenants");

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            assertThrows(Exception.class, () -> st.execute("INSERT INTO tenant_documents (tenant_id, property_id, "
                + "document_type, front_image_url, front_content_type) VALUES ('" + otherTenant + "', '" + propertyId
                + "', 'GPLX', 'k', 'image/png')"), "a GPLX row requires the encrypted number");
            assertThrows(Exception.class, () -> st.execute("INSERT INTO tenant_documents (tenant_id, property_id, "
                + "document_type, front_image_url, front_content_type) VALUES ('" + otherTenant + "', '" + propertyId
                + "', 'PASSPORT', 'k', 'image/png')"), "unknown document type must be rejected");
            assertEquals("1", firstValue(st, "SELECT count(*) FROM tenant_documents WHERE created_by IS NOT NULL "
                + "AND updated_by IS NOT NULL AND updated_at IS NOT NULL"));
        }

        repository.delete(tenantA, DocumentType.GPLX);
        assertTrue(repository.findByTenantIdAndType(tenantA, DocumentType.GPLX).isEmpty());
    }
}
