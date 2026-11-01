package com.phonghub.migration;

import com.phonghub.adapter.out.audit.PostgresAuditAdapter;
import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.AuditQueryPort;
import com.phonghub.application.port.out.TenantDataProtectionPort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #250: phạm vi xem nhật ký của nhân viên, kỹ thuật viên và người thuê được áp ngay ở câu truy vấn. */
@Testcontainers(disabledWithoutDocker = true)
class AuditScopePostgresTest {

    private static final String MAIN = "classpath:db/migration";
    private static final String FIXTURE = "classpath:db/seed/docker";

    private static final UUID STAFF_1 = UUID.fromString("22222222-2222-2222-2222-222222222221");
    private static final UUID TECH_1 = UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final UUID TENANT_USER = UUID.fromString("44444444-4444-4444-4444-444444444441");
    private static final UUID TENANT_ID = UUID.fromString("44444444-0000-0000-0000-444444444441");
    private static final UUID PROP_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PROP_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CONTRACT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID TICKET = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static NamedParameterJdbcTemplate jdbc;
    private static PostgresAuditAdapter audit;

    @BeforeAll
    static void migrateAndSeedEvents() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).load().migrate();
        TenantDataProtectionPort protector = new AesGcmTenantDataProtector("test-v1",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=");
        jdbc = new NamedParameterJdbcTemplate(ds);
        audit = new PostgresAuditAdapter(jdbc, new ObjectMapper(), protector);

        record("CONTRACT_UPDATED", STAFF_1, "CONTRACT", CONTRACT, Instant.now());
        record("CONTRACT_UPDATED_OLD", STAFF_1, "CONTRACT", CONTRACT, Instant.now().minus(30, ChronoUnit.DAYS));
        record("TICKET_UPDATED", TECH_1, "MAINTENANCE_TICKET", TICKET, Instant.now());
        record("PROPERTY_UPDATED", STAFF_1, "PROPERTY", PROP_B, Instant.now());
        record("LOGIN_SUCCEEDED", TENANT_USER, "USER", TENANT_USER, Instant.now());
        record("LOGIN_SUCCEEDED_STAFF", STAFF_1, "USER", STAFF_1, Instant.now());
        record("TENANT_DOCUMENT_VIEWED", STAFF_1, "TENANT_DOCUMENT", TENANT_ID, Instant.now());
        audit.recordEvent(new AuditPort.AuditEvent(UUID.randomUUID(), "SERVICE_RATE_UPDATED", STAFF_1,
            "SERVICE_RATE", UUID.randomUUID().toString(), Map.of(), Instant.now(), PROP_A));
        // Hóa đơn của hợp đồng người thuê và của một hợp đồng khác (không có hợp đồng nào khác trong fixture).
        jdbc.update("""
            INSERT INTO audit_logs (id, action, actor_id, target_type, target_id, property_id, contract_id, created_at)
            VALUES (:id, 'INVOICE_VOIDED', :actor, 'INVOICE', :target, :prop, :contract, now()),
                   (:id2, 'INVOICE_VOIDED_OTHER', :actor, 'INVOICE', :target, :prop, NULL, now())""",
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("id", UUID.randomUUID()).addValue("id2", UUID.randomUUID())
                .addValue("actor", STAFF_1).addValue("target", UUID.randomUUID().toString())
                .addValue("prop", PROP_A).addValue("contract", CONTRACT));
    }

    private static void record(String action, UUID actor, String type, UUID target, Instant at) {
        audit.recordEvent(new AuditPort.AuditEvent(UUID.randomUUID(), action, actor, type, target.toString(),
            Map.of(), at, null));
    }

    private static List<String> actions(AuditQueryPort.Scope scope) {
        return audit.search(new AuditQueryPort.Filter(null, null, null, null, null, null, 100, 0, scope))
            .stream().map(AuditQueryPort.Entry::action).toList();
    }

    @Test
    @DisplayName("sự kiện được gắn phòng, hợp đồng và người dùng liên quan lúc ghi")
    void referencesAreResolved() {
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT room_id, contract_id FROM audit_logs WHERE action = 'CONTRACT_UPDATED'", Map.of());
        assertEquals("10101010-1010-1010-1010-101010101010", String.valueOf(row.get("room_id")));
        assertEquals(CONTRACT.toString(), String.valueOf(row.get("contract_id")));
        assertEquals(TENANT_USER.toString(), jdbc.queryForObject(
            "SELECT subject_user_id::text FROM audit_logs WHERE action = 'TENANT_DOCUMENT_VIEWED'", Map.of(), String.class));
        assertEquals("10201020-1020-1020-1020-102010201020", jdbc.queryForObject(
            "SELECT room_id::text FROM audit_logs WHERE action = 'TICKET_UPDATED'", Map.of(), String.class));
    }

    @Test
    @DisplayName("nhân viên thấy sự kiện nhà trọ được giao và sự kiện của chính mình, không thấy nhà trọ khác")
    void staffScope() {
        List<String> seen = actions(new AuditQueryPort.Scope(Set.of(PROP_A), STAFF_1, null, null));
        assertTrue(seen.contains("CONTRACT_UPDATED"));
        assertTrue(seen.contains("TICKET_UPDATED"), "phiếu của phòng thuộc nhà trọ được giao");
        assertTrue(seen.contains("SERVICE_RATE_UPDATED"));
        assertTrue(seen.contains("LOGIN_SUCCEEDED_STAFF"), "thao tác của chính mình");
        assertTrue(seen.contains("PROPERTY_UPDATED"), "do chính nhân viên thực hiện nên vẫn thấy");
        assertEquals(List.of(), actions(new AuditQueryPort.Scope(Set.of(PROP_B), null, null, null)).stream()
            .filter(a -> a.equals("CONTRACT_UPDATED") || a.equals("TICKET_UPDATED")).toList(),
            "nhà trọ khác không thấy sự kiện của nhà A");
    }

    @Test
    @DisplayName("kỹ thuật viên chỉ thấy sự kiện của phiếu được giao và thao tác của mình")
    void technicianScope() {
        List<String> seen = actions(new AuditQueryPort.Scope(null, TECH_1, TECH_1, null));
        assertEquals(List.of("TICKET_UPDATED"), seen);
    }

    @Test
    @DisplayName("người thuê thấy giao dịch, tài khoản của mình và phòng đang thuê từ ngày kích hoạt; không thấy sự kiện nội bộ")
    void tenantScope() {
        List<String> seen = actions(new AuditQueryPort.Scope(null, null, null, TENANT_USER));
        assertTrue(seen.contains("INVOICE_VOIDED"), "giao dịch của hợp đồng mình");
        assertTrue(seen.contains("LOGIN_SUCCEEDED"), "tài khoản của mình");
        assertTrue(seen.contains("CONTRACT_UPDATED"), "sự kiện hợp đồng/phòng đang hiệu lực");
        assertTrue(!seen.contains("CONTRACT_UPDATED_OLD"), "trước ngày hợp đồng kích hoạt thì không thấy");
        assertTrue(!seen.contains("INVOICE_VOIDED_OTHER"), "hóa đơn không thuộc hợp đồng của mình");
        assertTrue(!seen.contains("TICKET_UPDATED"), "phiếu của phòng khác");
        assertTrue(!seen.contains("SERVICE_RATE_UPDATED"), "đơn giá dịch vụ là sự kiện nội bộ");
        assertTrue(!seen.contains("TENANT_DOCUMENT_VIEWED"), "việc người khác xem giấy tờ không hiện");
        assertTrue(!seen.contains("LOGIN_SUCCEEDED_STAFF"), "tài khoản của người khác");
        assertTrue(!seen.contains("PROPERTY_UPDATED"));
    }

    @Test
    @DisplayName("phạm vi rỗng thì không thấy gì")
    void emptyScopeSeesNothing() {
        assertEquals(List.of(), actions(new AuditQueryPort.Scope(Set.of(), null, null, null)));
    }
}
