package db.migration;

import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.application.port.out.TenantDataProtectionPort;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V14__encrypt_tenant_identity_cards extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String keyId = getEnvironmentOrFallback("TENANT_DATA_KEY_ID", "v1");
        String encryptionKey = getEnvironmentOrFallback("TENANT_DATA_ENCRYPTION_KEY", "phonghub_default_tenant_encryption_key_32bytes");
        String hmacKey = getEnvironmentOrFallback("TENANT_DATA_HMAC_KEY", "phonghub_default_tenant_hmac_key_32bytes_long");
        TenantDataProtectionPort protector = new AesGcmTenantDataProtector(keyId, encryptionKey, hmacKey);

        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE tenants ADD COLUMN identity_card_ciphertext TEXT");
            statement.execute("ALTER TABLE tenants ADD COLUMN identity_card_nonce TEXT");
            statement.execute("ALTER TABLE tenants ADD COLUMN identity_card_hmac CHAR(64)");
            statement.execute("ALTER TABLE tenants ADD COLUMN identity_card_key_id VARCHAR(64)");
        }

        List<LegacyTenant> tenants = new ArrayList<>();
        try (Statement statement = context.getConnection().createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, identity_card_number FROM tenants FOR UPDATE")) {
            while (rows.next()) {
                tenants.add(new LegacyTenant(
                    rows.getObject("id", UUID.class),
                    rows.getString("identity_card_number")
                ));
            }
        }

        try (PreparedStatement update = context.getConnection().prepareStatement("""
            UPDATE tenants
            SET identity_card_ciphertext = ?, identity_card_nonce = ?,
                identity_card_hmac = ?, identity_card_key_id = ?
            WHERE id = ?
            """)) {
            for (LegacyTenant tenant : tenants) {
                TenantDataProtectionPort.ProtectedValue value = protector.protect(tenant.identityCardNumber());
                update.setString(1, value.ciphertext());
                update.setString(2, value.nonce());
                update.setString(3, value.fingerprint());
                update.setString(4, value.keyId());
                update.setObject(5, tenant.id());
                update.addBatch();
            }
            update.executeBatch();
        }

        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE tenants DROP CONSTRAINT IF EXISTS tenants_identity_card_number_key");
            statement.execute("ALTER TABLE tenants DROP COLUMN identity_card_number");
            statement.execute("ALTER TABLE tenants ALTER COLUMN identity_card_ciphertext SET NOT NULL");
            statement.execute("ALTER TABLE tenants ALTER COLUMN identity_card_nonce SET NOT NULL");
            statement.execute("ALTER TABLE tenants ALTER COLUMN identity_card_hmac SET NOT NULL");
            statement.execute("ALTER TABLE tenants ALTER COLUMN identity_card_key_id SET NOT NULL");
            statement.execute("ALTER TABLE tenants ADD CONSTRAINT uk_tenants_identity_card_hmac UNIQUE (identity_card_hmac)");
        }
    }

    private static String getEnvironmentOrFallback(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private record LegacyTenant(UUID id, String identityCardNumber) {}
}
