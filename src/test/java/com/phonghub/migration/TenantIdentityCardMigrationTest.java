package com.phonghub.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class TenantIdentityCardMigrationTest {

    private static final String LOCATIONS_MAIN = "classpath:db/migration";
    private static final String LOCATIONS_FIXTURE = "classpath:db/seed/docker";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private PGSimpleDataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    @Test
    @DisplayName("V14 encrypts legacy CCCD, drops plaintext column and keeps duplicate protection via HMAC")
    void v14EncryptsLegacyIdentityCards() throws Exception {
        PGSimpleDataSource ds = dataSource();

        Flyway.configure().dataSource(ds).locations(LOCATIONS_MAIN, LOCATIONS_FIXTURE)
            .target("13").load().migrate();

        UUID legacyId = UUID.randomUUID();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO tenants (id, full_name, identity_card_number, phone) VALUES ('"
                + legacyId + "', 'Nguoi Cu', '079200001234', '0900000099')");
        }

        Flyway.configure().dataSource(ds).locations(LOCATIONS_MAIN, LOCATIONS_FIXTURE)
            .load().migrate();

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            ResultSet columns = st.executeQuery("SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'tenants' AND column_name = 'identity_card_number'");
            assertFalse(columns.next(), "plaintext column must be dropped");

            ResultSet row = st.executeQuery("SELECT identity_card_ciphertext, identity_card_nonce, "
                + "identity_card_hmac, identity_card_key_id FROM tenants WHERE id = '" + legacyId + "'");
            assertTrue(row.next());
            assertNotEquals("079200001234", row.getString("identity_card_ciphertext"));
            assertFalse(row.getString("identity_card_ciphertext").contains("079200001234"));
            assertEquals(64, row.getString("identity_card_hmac").trim().length());
            assertEquals("test-v1", row.getString("identity_card_key_id"));

            ResultSet leftovers = st.executeQuery("SELECT count(*) FROM tenants "
                + "WHERE identity_card_ciphertext IS NULL OR identity_card_hmac IS NULL");
            leftovers.next();
            assertEquals(0, leftovers.getInt(1), "every row (including docker fixtures) must be encrypted");
        }

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            ResultSet source = st.executeQuery("SELECT identity_card_ciphertext, identity_card_nonce, "
                + "identity_card_hmac, identity_card_key_id FROM tenants WHERE id = '" + legacyId + "'");
            source.next();
            String insert = "INSERT INTO tenants (id, full_name, phone, identity_card_ciphertext, "
                + "identity_card_nonce, identity_card_hmac, identity_card_key_id) VALUES ('"
                + UUID.randomUUID() + "', 'Trung CCCD', '0900000098', 'x', 'y', '"
                + source.getString("identity_card_hmac") + "', 'test-v1')";
            assertThrows(java.sql.SQLException.class, () -> st.execute(insert),
                "duplicate HMAC must violate the unique constraint");
        }
    }
}
