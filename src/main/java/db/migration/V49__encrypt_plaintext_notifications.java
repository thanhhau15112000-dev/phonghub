package db.migration;

import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.adapter.out.security.PersonalDataFields;
import com.phonghub.application.port.out.TenantDataProtectionPort;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Mã hóa các thông báo được chèn thẳng bằng SQL dạng thường (không qua ứng dụng): một dòng như vậy làm hỏng mọi truy
 * vấn đọc thông báo. Sau đó DB chặn luôn việc ghi bản rõ vào các cột title, message, resolution_note.
 * Cần các biến môi trường TENANT_DATA_* giống migration V17.
 */
public class V49__encrypt_plaintext_notifications extends BaseJavaMigration {

    private static final String PREFIX = "enc1.";

    @Override
    public void migrate(Context context) throws Exception {
        TenantDataProtectionPort protector = new AesGcmTenantDataProtector(
            getEnvironmentOrFallback("TENANT_DATA_KEY_ID", "v1"),
            getEnvironmentOrFallback("TENANT_DATA_ENCRYPTION_KEY", "phonghub_default_tenant_encryption_key_32bytes"),
            getEnvironmentOrFallback("TENANT_DATA_HMAC_KEY", "phonghub_default_tenant_hmac_key_32bytes_long")
        );
        Connection connection = context.getConnection();

        List<String[]> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                 SELECT id, title, message, resolution_note FROM notifications
                 WHERE title NOT LIKE 'enc1.%' OR message NOT LIKE 'enc1.%'
                    OR (resolution_note IS NOT NULL AND resolution_note NOT LIKE 'enc1.%')
                 FOR UPDATE""")) {
            while (result.next()) {
                rows.add(new String[]{result.getString(1), result.getString(2), result.getString(3), result.getString(4)});
            }
        }
        try (PreparedStatement update = connection.prepareStatement(
            "UPDATE notifications SET title = ?, message = ?, resolution_note = ? WHERE id = ?")) {
            for (String[] row : rows) {
                update.setString(1, encrypt(protector, PersonalDataFields.NOTIFICATIONS_TITLE, row[1]));
                update.setString(2, encrypt(protector, PersonalDataFields.NOTIFICATIONS_MESSAGE, row[2]));
                update.setString(3, encrypt(protector, PersonalDataFields.NOTIFICATIONS_RESOLUTION_NOTE, row[3]));
                update.setObject(4, UUID.fromString(row[0]));
                update.addBatch();
            }
            update.executeBatch();
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                ALTER TABLE notifications ADD CONSTRAINT notifications_fields_encrypted CHECK (
                    title LIKE 'enc1.%' AND message LIKE 'enc1.%'
                    AND (resolution_note IS NULL OR resolution_note LIKE 'enc1.%'))""");
        }
    }

    private static String encrypt(TenantDataProtectionPort protector, String purpose, String value) {
        if (value == null || value.startsWith(PREFIX)) {
            return value;
        }
        return protector.encryptField(purpose, value);
    }

    private static String getEnvironmentOrFallback(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
