package com.phonghub.migration;

import com.phonghub.adapter.out.audit.PostgresAuditAdapter;
import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.AuditQueryPort;
import com.phonghub.application.port.out.TenantDataProtectionPort;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #78: ghi nhật ký có làm sạch + suy ra nhà trọ từ đối tượng, truy vấn có lọc theo phạm vi nhà trọ. */
@Testcontainers(disabledWithoutDocker = true)
class AuditLogPostgresTest {

    private static final String MAIN = "classpath:db/migration";
    private static final String FIXTURE = "classpath:db/seed/docker";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private String scalar(Statement st, String sql) throws Exception {
        ResultSet rs = st.executeQuery(sql);
        rs.next();
        return rs.getString(1);
    }

    @Test
    @DisplayName("sự kiện được gán nhà trọ từ phòng/hợp đồng, bị che dữ liệu nhạy cảm, và lọc đúng theo phạm vi")
    void recordAndQuery() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).load().migrate();

        UUID roomId;
        UUID roomProperty;
        UUID otherProperty;
        UUID actor;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            roomId = UUID.fromString(scalar(st, "SELECT id FROM rooms LIMIT 1"));
            roomProperty = UUID.fromString(scalar(st, "SELECT property_id FROM rooms WHERE id = '" + roomId + "'"));
            otherProperty = UUID.randomUUID();
            st.execute("INSERT INTO properties (id, name, address, total_rooms) VALUES ('" + otherProperty
                + "', 'Nhà khác', 'addr', 1)");
            actor = UUID.fromString(scalar(st, "SELECT id FROM users LIMIT 1"));
        }

        TenantDataProtectionPort protector = new AesGcmTenantDataProtector("test-v1",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=");
        PostgresAuditAdapter audit = new PostgresAuditAdapter(new NamedParameterJdbcTemplate(ds), new ObjectMapper(), protector);

        audit.recordEvent(AuditPort.AuditEvent.of("ROOM_UPDATED", actor, "ROOM", roomId.toString(),
            Map.of("note", "đổi giá", "password", "hunter2", "identityCardNumber", "079201004321")));
        audit.recordEvent(AuditPort.AuditEvent.of("PROPERTY_UPDATED", actor, "PROPERTY", otherProperty.toString(),
            Map.of("note", "nhà khác")));
        audit.recordEvent(AuditPort.AuditEvent.of("LOGIN_FAILED", null, "USER", null, Map.of("reason", "INVALID_CREDENTIALS")));

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            assertEquals(roomProperty.toString(),
                scalar(st, "SELECT property_id FROM audit_logs WHERE action = 'ROOM_UPDATED'"));
            assertEquals(otherProperty.toString(),
                scalar(st, "SELECT property_id FROM audit_logs WHERE action = 'PROPERTY_UPDATED'"));
            String stored = scalar(st, "SELECT details FROM audit_logs WHERE action = 'ROOM_UPDATED'");
            assertTrue(stored.startsWith("enc1."), "chi tiết vẫn được mã hóa khi lưu");
        }

        List<AuditQueryPort.Entry> mine = audit.search(new AuditQueryPort.Filter(
            List.of(roomProperty), null, null, null, null, null, 50, 0));
        assertEquals(1, mine.size());
        AuditQueryPort.Entry entry = mine.getFirst();
        assertEquals("ROOM_UPDATED", entry.action());
        assertTrue(entry.details().contains("đổi giá"));
        assertFalse(entry.details().contains("hunter2"), "mật khẩu không được lưu");
        assertFalse(entry.details().contains("079201004321"), "số CCCD đầy đủ không được lưu");
        assertTrue(entry.details().contains("********4321"));

        assertEquals(3, audit.search(new AuditQueryPort.Filter(null, null, null, null, null, null, 50, 0)).size(),
            "admin (không giới hạn nhà trọ) thấy cả sự kiện không gắn nhà trọ");
        assertTrue(audit.search(new AuditQueryPort.Filter(List.of(), null, null, null, null, null, 50, 0)).isEmpty());
        assertEquals(1, audit.search(new AuditQueryPort.Filter(
            null, null, "USER", "LOGIN_FAILED", null, null, 50, 0)).size());
        assertTrue(audit.search(new AuditQueryPort.Filter(
            null, null, null, null, Instant.now().plusSeconds(3600), null, 50, 0)).isEmpty());
        assertEquals(1, audit.search(new AuditQueryPort.Filter(
            null, null, null, null, null, null, 1, 1)).size(), "phân trang");
    }
}
