package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresNotificationRepository;
import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.domain.model.Notification;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** V49 trên Postgres thật: thông báo chèn bằng SQL dạng thường được mã hóa, sau đó DB chặn ghi bản rõ. */
@Testcontainers(disabledWithoutDocker = true)
class PlaintextNotificationMigrationPostgresTest {

    private static final String MAIN = "classpath:db/migration";
    private static final String FIXTURE = "classpath:db/seed/docker";
    private static final UUID TENANT_USER = UUID.fromString("44444444-4444-4444-4444-444444444441");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("V49 encrypts plaintext notification rows so they load again, then rejects plaintext writes")
    void v49EncryptsPlaintextNotifications() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).target("48").load().migrate();

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        // Cùng khóa với biến môi trường TENANT_DATA_* mà surefire đặt cho migration
        var protector = new AesGcmTenantDataProtector("test-v1",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=");
        PostgresNotificationRepository notifications = new PostgresNotificationRepository(jdbc, protector);

        UUID plainId = UUID.randomUUID();
        UUID resolvedId = UUID.randomUUID();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO notifications (id, type, title, message, target_user_id, created_at) VALUES ('"
                + plainId + "', 'GENERAL', 'Hóa đơn tháng 10', 'Còn nợ 3.000.000 đ', '" + TENANT_USER
                + "', now() - interval '1 hour')");
            st.execute("INSERT INTO notifications (id, type, title, message, resolved, resolution_note) VALUES ('"
                + resolvedId + "', 'PAYMENT_RECEIVED', 'Tiền về không khớp', 'STK 9704123456789', true, 'Đã đối soát')");
        }
        Notification alreadyEncrypted = notifications.save(Notification.createForUser("Phiếu sửa chữa",
            "Đã hoàn tất", notificationsTarget(jdbc, protector), "/maintenance"));
        String encryptedTitleBefore = rawTitle(jdbc, alreadyEncrypted.id());

        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).load().migrate();

        List<Map<String, Object>> raw = jdbc.queryForList(
            "SELECT title, message, resolution_note FROM notifications", Map.of());
        assertTrue(raw.stream().noneMatch(row -> row.toString().contains("Hóa đơn tháng 10")
            || row.toString().contains("9704123456789") || row.toString().contains("Đã đối soát")), raw.toString());
        assertEquals(encryptedTitleBefore, rawTitle(jdbc, alreadyEncrypted.id()), "dòng đã mã hóa giữ nguyên");

        Notification loaded = notifications.findById(plainId).orElseThrow();
        assertEquals("Hóa đơn tháng 10", loaded.title());
        assertEquals("Còn nợ 3.000.000 đ", loaded.message());
        assertEquals("Đã đối soát", notifications.findById(resolvedId).orElseThrow().resolutionNote());
        assertEquals(3, notifications.findAll().size());

        assertThrows(DataAccessException.class, () -> jdbc.update("""
            INSERT INTO notifications (id, type, title, message) VALUES (:id, 'GENERAL', 'bản rõ', 'bản rõ')
            """, Map.of("id", UUID.randomUUID())));
    }

    private static com.phonghub.domain.model.User notificationsTarget(
        NamedParameterJdbcTemplate jdbc, AesGcmTenantDataProtector protector
    ) {
        return new com.phonghub.adapter.out.persistence.postgres.PostgresUserRepository(jdbc, protector)
            .findById(TENANT_USER).orElseThrow();
    }

    private static String rawTitle(NamedParameterJdbcTemplate jdbc, UUID id) {
        return jdbc.queryForObject("SELECT title FROM notifications WHERE id = :id", Map.of("id", id), String.class);
    }
}
