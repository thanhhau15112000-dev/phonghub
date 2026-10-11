package com.phonghub.migration;

import com.phonghub.adapter.out.audit.PostgresAuditAdapter;
import com.phonghub.adapter.out.persistence.postgres.PostgresPaymentRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresTenantRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresUserRepository;
import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.TenantDataProtectionPort;
import com.phonghub.domain.model.PaymentTransaction;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PersonalDataEncryptionPostgresTest {

    private static final String MAIN = "classpath:db/migration";
    private static final String FIXTURE = "classpath:db/seed/docker";
    private static final String[] SENSITIVE = {
        "Lê Văn Cũ", "legacy.user@example.com", "legacyuser", "0911222333",
        "Nguyễn Thị Khách", "khach@example.com", "12 Nguyễn Trãi, Quận 5", "0988777666",
        "9704123456789", "NGUYEN VAN A chuyen khoan", "secret-audit-name"
    };

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private String text(Statement st, String sql) throws Exception {
        ResultSet rs = st.executeQuery(sql);
        rs.next();
        return rs.getString(1);
    }

    @Test
    @DisplayName("V17 encrypts users, tenants, SePay transactions and audit details while login lookups keep working")
    void v17EncryptsLegacyPersonalData() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).target("16").load().migrate();

        TenantDataProtectionPort protector = new AesGcmTenantDataProtector("test-v1",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=");
        TenantDataProtectionPort.ProtectedValue identity = protector.protect("079201004321");
        UUID legacyUser = UUID.randomUUID();
        UUID legacyTenant = UUID.randomUUID();
        UUID legacyTx = UUID.randomUUID();
        UUID legacyAudit = UUID.randomUUID();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO users (id, email, full_name, phone, role, status, username) VALUES ('"
                + legacyUser + "', 'Legacy.User@Example.com', 'Lê Văn Cũ', '0911222333', 'TENANT', 'ACTIVE', 'legacyuser')");
            st.execute("INSERT INTO tenants (id, full_name, phone, email, permanent_address, identity_card_ciphertext, "
                + "identity_card_nonce, identity_card_hmac, identity_card_key_id) VALUES ('" + legacyTenant
                + "', 'Nguyễn Thị Khách', '0988777666', 'khach@example.com', '12 Nguyễn Trãi, Quận 5', '"
                + identity.ciphertext() + "', '" + identity.nonce() + "', '" + identity.fingerprint() + "', '"
                + identity.keyId() + "')");
            st.execute("INSERT INTO sepay_transactions (id, sepay_id, gateway, account_number, transfer_amount, "
                + "content, description) VALUES ('" + legacyTx + "', 987654321, 'MBBank', '9704123456789', 100000, "
                + "'NGUYEN VAN A chuyen khoan', 'NGUYEN VAN A chuyen khoan')");
            st.execute("INSERT INTO audit_logs (id, action, details) VALUES ('" + legacyAudit
                + "', 'LEGACY', '{\"fullName\":\"secret-audit-name\"}')");
        }

        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).load().migrate();

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            for (String table : List.of("users", "tenants", "sepay_transactions", "audit_logs")) {
                String dump = text(st, "SELECT string_agg(t::text, ' ') FROM " + table + " t");
                for (String secret : SENSITIVE) {
                    assertFalse(dump.contains(secret), table + " must not contain plaintext: " + secret);
                }
            }
            assertEquals("0", text(st, "SELECT count(*) FROM users WHERE email NOT LIKE 'enc1.%' "
                + "OR full_name NOT LIKE 'enc1.%' OR (phone IS NOT NULL AND phone NOT LIKE 'enc1.%') "
                + "OR (username IS NOT NULL AND username NOT LIKE 'enc1.%')"), "every user row incl. fixtures");
            assertEquals("0", text(st, "SELECT count(*) FROM tenants WHERE full_name NOT LIKE 'enc1.%' "
                + "OR phone NOT LIKE 'enc1.%'"), "every tenant row incl. fixtures");
            assertEquals("0", text(st, "SELECT count(*) FROM users WHERE email_hmac IS NULL"));
            assertEquals("0", text(st, "SELECT count(*) FROM pg_indexes WHERE tablename = 'users' AND "
                + "indexdef LIKE '%UNIQUE%' AND (indexdef LIKE '%(email)%' OR indexdef LIKE '%lower(email%' "
                + "OR indexdef LIKE '%(username)%' OR indexdef LIKE '%lower(username%' OR indexdef LIKE '%(phone)%')"),
                "no unique index may remain on plaintext columns");
        }

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        PostgresUserRepository users = new PostgresUserRepository(jdbc, protector);

        User byEmail = users.findByEmail("  LEGACY.USER@example.com ").orElseThrow();
        assertEquals(legacyUser, byEmail.id());
        assertEquals("Lê Văn Cũ", byEmail.fullName());
        assertEquals("0911222333", byEmail.phone());
        assertEquals("legacyuser", users.findByUsername("LegacyUser").orElseThrow().username());
        assertTrue(users.findByEmail("nobody@example.com").isEmpty());
        assertTrue(users.findById(legacyUser).isPresent());
        assertTrue(users.findAll().stream().anyMatch(u -> u.id().equals(legacyUser)));
        assertFalse(users.findByRole(UserRole.TENANT).isEmpty());

        User created = users.save(new User(UUID.randomUUID(), "new.user", "new.user@example.com", "Trần Mới",
            "0933444555", UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now()));
        assertEquals("Trần Mới", users.findByUsername("NEW.USER").orElseThrow().fullName());
        assertThrows(Exception.class, () -> users.save(new User(UUID.randomUUID(), "other", "NEW.user@Example.com",
            "Trùng Email", "0900000123", UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now())),
            "email must stay unique regardless of case");
        assertThrows(Exception.class, () -> users.save(new User(UUID.randomUUID(), "NEW.USER", "x@example.com",
            "Trùng Username", "0900000124", UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now())),
            "username must stay unique regardless of case");
        assertThrows(Exception.class, () -> users.save(new User(UUID.randomUUID(), "u3", "u3@example.com",
            "Trùng SĐT", "0933 444 555", UserRole.TENANT, User.UserStatus.ACTIVE, false, Instant.now())),
            "phone must stay unique after normalization");
        users.save(new User(created.id(), "new.user", "new.user@example.com", "Trần Đổi Tên", "0933444555",
            UserRole.TENANT, User.UserStatus.ACTIVE, false, created.createdAt()));
        assertEquals("Trần Đổi Tên", users.findById(created.id()).orElseThrow().fullName());

        PostgresTenantRepository tenants = new PostgresTenantRepository(jdbc, protector);
        Tenant tenant = tenants.findById(legacyTenant).orElseThrow();
        assertEquals("Nguyễn Thị Khách", tenant.fullName());
        assertEquals("khach@example.com", tenant.email());
        assertEquals("12 Nguyễn Trãi, Quận 5", tenant.permanentAddress());
        assertEquals("079201004321", tenant.identityCardNumber());
        Tenant saved = tenants.save(new Tenant(UUID.randomUUID(), null, "Phạm Mới", "079201009999", "0977111222",
            null, null, Instant.now()));
        Tenant reread = tenants.findById(saved.id()).orElseThrow();
        assertEquals("Phạm Mới", reread.fullName());
        assertEquals("0977111222", reread.phone());
        assertEquals(null, reread.email());

        PostgresPaymentRepository payments = new PostgresPaymentRepository(jdbc, protector);
        PaymentTransaction tx = payments.findBySepayId(987654321L).orElseThrow();
        assertEquals("9704123456789", tx.accountNumber());
        assertEquals("NGUYEN VAN A chuyen khoan", tx.content());
        assertTrue(payments.saveIfNew(new PaymentTransaction(UUID.randomUUID(), 111L, "MBBank", null, "0011223344",
            "VA01", "in", new BigDecimal("5000"), null, "PH1", "noi dung moi", "REF", "mo ta moi", null, null, null,
            null)));
        PaymentTransaction fresh = payments.findBySepayId(111L).orElseThrow();
        assertEquals("0011223344", fresh.accountNumber());
        assertEquals("VA01", fresh.subAccount());
        assertEquals("noi dung moi", fresh.content());
        assertEquals("PH1", fresh.code());

        new PostgresAuditAdapter(jdbc, new ObjectMapper(), protector).recordEvent(AuditPort.AuditEvent.of(
            "TEST_EVENT", legacyUser, "USER", legacyUser.toString(), Map.of("fullName", "Tên Mới Trong Audit")));
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            String details = text(st, "SELECT details FROM audit_logs WHERE action = 'TEST_EVENT'");
            assertTrue(details.startsWith("enc1."));
            assertFalse(details.contains("Tên Mới"));
            assertEquals("{\"fullName\":\"Tên Mới Trong Audit\"}",
                protector.decryptField("audit_logs.details", details));
        }
    }
}
