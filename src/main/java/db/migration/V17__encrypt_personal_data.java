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
 * Mã hóa toàn bộ thông tin cá nhân đang lưu dạng thường: users, tenants, giao dịch SePay và chi tiết audit.
 * Email, username và số điện thoại được thay bằng chỉ mục mù HMAC để vẫn tra cứu và chống trùng được.
 * Cần các biến môi trường TENANT_DATA_* giống migration V14; thiếu khóa thì migration dừng an toàn.
 */
public class V17__encrypt_personal_data extends BaseJavaMigration {

    private static final String PREFIX = "enc1.";

    @Override
    public void migrate(Context context) throws Exception {
        TenantDataProtectionPort protector = new AesGcmTenantDataProtector(
            requiredEnvironment("TENANT_DATA_KEY_ID"),
            requiredEnvironment("TENANT_DATA_ENCRYPTION_KEY"),
            requiredEnvironment("TENANT_DATA_HMAC_KEY")
        );
        Connection connection = context.getConnection();
        migrateUsers(connection, protector);
        migrateTenants(connection, protector);
        migrateSepayTransactions(connection, protector);
        migrateAuditLogs(connection, protector);
    }

    private void migrateUsers(Connection connection, TenantDataProtectionPort protector) throws Exception {
        execute(connection,
            "ALTER TABLE users ADD COLUMN email_hmac CHAR(64)",
            "ALTER TABLE users ADD COLUMN username_hmac CHAR(64)",
            "ALTER TABLE users ADD COLUMN phone_hmac CHAR(64)",
            "ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key",
            "ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_key",
            "ALTER TABLE users DROP CONSTRAINT IF EXISTS users_phone_key",
            "DROP INDEX IF EXISTS uk_users_lower_email",
            "DROP INDEX IF EXISTS uk_users_lower_username",
            "DROP INDEX IF EXISTS uk_users_phone",
            "ALTER TABLE users ALTER COLUMN email TYPE TEXT",
            "ALTER TABLE users ALTER COLUMN username TYPE TEXT",
            "ALTER TABLE users ALTER COLUMN full_name TYPE TEXT",
            "ALTER TABLE users ALTER COLUMN phone TYPE TEXT");

        List<String[]> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT id, email, username, full_name, phone FROM users FOR UPDATE")) {
            while (result.next()) {
                rows.add(new String[]{result.getString(1), result.getString(2), result.getString(3),
                    result.getString(4), result.getString(5)});
            }
        }
        try (PreparedStatement update = connection.prepareStatement("""
            UPDATE users SET email = ?, email_hmac = ?, username = ?, username_hmac = ?,
                full_name = ?, phone = ?, phone_hmac = ? WHERE id = ?
            """)) {
            for (String[] row : rows) {
                String email = plain(row[1]);
                String username = plain(row[2]);
                String phone = plain(row[4]);
                update.setString(1, encrypt(protector, PersonalDataFields.USERS_EMAIL, row[1]));
                update.setString(2, protector.blindIndex(PersonalDataFields.USERS_EMAIL,
                    PersonalDataFields.normalizeText(email)));
                update.setString(3, encrypt(protector, PersonalDataFields.USERS_USERNAME, row[2]));
                update.setString(4, protector.blindIndex(PersonalDataFields.USERS_USERNAME,
                    PersonalDataFields.normalizeText(username)));
                update.setString(5, encrypt(protector, PersonalDataFields.USERS_FULL_NAME, row[3]));
                update.setString(6, encrypt(protector, PersonalDataFields.USERS_PHONE, row[4]));
                update.setString(7, protector.blindIndex(PersonalDataFields.USERS_PHONE,
                    PersonalDataFields.normalizePhone(phone)));
                update.setObject(8, UUID.fromString(row[0]));
                update.addBatch();
            }
            update.executeBatch();
        }

        execute(connection,
            "ALTER TABLE users ALTER COLUMN email_hmac SET NOT NULL",
            "CREATE UNIQUE INDEX uk_users_email_hmac ON users (email_hmac)",
            "CREATE UNIQUE INDEX uk_users_username_hmac ON users (username_hmac) WHERE username_hmac IS NOT NULL",
            "CREATE UNIQUE INDEX uk_users_phone_hmac ON users (phone_hmac) WHERE phone_hmac IS NOT NULL");
    }

    private void migrateTenants(Connection connection, TenantDataProtectionPort protector) throws Exception {
        execute(connection,
            "ALTER TABLE tenants ADD COLUMN phone_hmac CHAR(64)",
            "ALTER TABLE tenants ALTER COLUMN full_name TYPE TEXT",
            "ALTER TABLE tenants ALTER COLUMN phone TYPE TEXT",
            "ALTER TABLE tenants ALTER COLUMN email TYPE TEXT",
            "ALTER TABLE tenants ALTER COLUMN permanent_address TYPE TEXT");

        List<String[]> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT id, full_name, phone, email, permanent_address FROM tenants FOR UPDATE")) {
            while (result.next()) {
                rows.add(new String[]{result.getString(1), result.getString(2), result.getString(3),
                    result.getString(4), result.getString(5)});
            }
        }
        try (PreparedStatement update = connection.prepareStatement("""
            UPDATE tenants SET full_name = ?, phone = ?, phone_hmac = ?, email = ?, permanent_address = ?
            WHERE id = ?
            """)) {
            for (String[] row : rows) {
                update.setString(1, encrypt(protector, PersonalDataFields.TENANTS_FULL_NAME, row[1]));
                update.setString(2, encrypt(protector, PersonalDataFields.TENANTS_PHONE, row[2]));
                update.setString(3, protector.blindIndex(PersonalDataFields.TENANTS_PHONE,
                    PersonalDataFields.normalizePhone(plain(row[2]))));
                update.setString(4, encrypt(protector, PersonalDataFields.TENANTS_EMAIL, row[3]));
                update.setString(5, encrypt(protector, PersonalDataFields.TENANTS_ADDRESS, row[4]));
                update.setObject(6, UUID.fromString(row[0]));
                update.addBatch();
            }
            update.executeBatch();
        }
        execute(connection, "CREATE INDEX idx_tenants_phone_hmac ON tenants (phone_hmac)");
    }

    private void migrateSepayTransactions(Connection connection, TenantDataProtectionPort protector)
        throws Exception {
        execute(connection,
            "ALTER TABLE sepay_transactions ALTER COLUMN account_number TYPE TEXT",
            "ALTER TABLE sepay_transactions ALTER COLUMN sub_account TYPE TEXT");

        List<String[]> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT id, account_number, sub_account, content, description FROM sepay_transactions FOR UPDATE")) {
            while (result.next()) {
                rows.add(new String[]{result.getString(1), result.getString(2), result.getString(3),
                    result.getString(4), result.getString(5)});
            }
        }
        try (PreparedStatement update = connection.prepareStatement("""
            UPDATE sepay_transactions SET account_number = ?, sub_account = ?, content = ?, description = ?
            WHERE id = ?
            """)) {
            for (String[] row : rows) {
                update.setString(1, encrypt(protector, PersonalDataFields.SEPAY_ACCOUNT_NUMBER, row[1]));
                update.setString(2, encrypt(protector, PersonalDataFields.SEPAY_SUB_ACCOUNT, row[2]));
                update.setString(3, encrypt(protector, PersonalDataFields.SEPAY_CONTENT, row[3]));
                update.setString(4, encrypt(protector, PersonalDataFields.SEPAY_DESCRIPTION, row[4]));
                update.setObject(5, UUID.fromString(row[0]));
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private void migrateAuditLogs(Connection connection, TenantDataProtectionPort protector) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                 "SELECT id, details FROM audit_logs WHERE details IS NOT NULL FOR UPDATE")) {
            while (result.next()) {
                rows.add(new String[]{result.getString(1), result.getString(2)});
            }
        }
        try (PreparedStatement update = connection.prepareStatement("UPDATE audit_logs SET details = ? WHERE id = ?")) {
            for (String[] row : rows) {
                update.setString(1, encrypt(protector, PersonalDataFields.AUDIT_DETAILS, row[1]));
                update.setObject(2, UUID.fromString(row[0]));
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private static void execute(Connection connection, String... statements) throws Exception {
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private static String encrypt(TenantDataProtectionPort protector, String purpose, String value) {
        if (value == null || value.startsWith(PREFIX)) {
            return value;
        }
        return protector.encryptField(purpose, value);
    }

    private static String plain(String value) {
        return value == null || value.startsWith(PREFIX) ? null : value;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for personal data migration V17");
        }
        return value;
    }
}
