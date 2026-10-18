package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresTenantPrivacyRepository;
import com.phonghub.domain.model.TenantPrivacy;
import java.time.Instant;
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

/** #71 trên Postgres thật: giữ pháp lý, yêu cầu xóa sớm, đã xóa và đã báo owner được lưu và ghi đè đúng. */
@Testcontainers(disabledWithoutDocker = true)
class TenantPrivacyPostgresTest {

    private static final UUID TENANT = UUID.fromString("44444444-0000-0000-0000-444444444441");
    private static final UUID ADMIN = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("Privacy state round-trips through every transition and the database rejects a hold without reason")
    void privacyLifecycle() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .load().migrate();

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        PostgresTenantPrivacyRepository privacy = new PostgresTenantPrivacyRepository(jdbc);

        assertTrue(privacy.find(TENANT).isEmpty());
        privacy.save(TenantPrivacy.none(TENANT));
        assertEquals(TenantPrivacy.none(TENANT), privacy.find(TENANT).orElseThrow());

        TenantPrivacy held = TenantPrivacy.none(TENANT).withLegalHold("  Tranh chấp tiền cọc  ", ADMIN, T0);
        privacy.save(held);
        assertEquals(held, privacy.find(TENANT).orElseThrow());
        assertEquals("Tranh chấp tiền cọc", privacy.find(TENANT).orElseThrow().legalHoldReason());

        // ghi đè toàn bộ trạng thái: bỏ giữ pháp lý xóa sạch thông tin người đặt
        TenantPrivacy done = held.withErasureRequested(T0.plusSeconds(60))
            .withErasureRequested(T0.plusSeconds(999))
            .withPurgeNotified(T0.plusSeconds(120))
            .withPurged(T0.plusSeconds(180))
            .withoutLegalHold();
        privacy.save(done);
        TenantPrivacy loaded = privacy.find(TENANT).orElseThrow();
        assertEquals(done, loaded);
        assertEquals(T0.plusSeconds(60), loaded.erasureRequestedAt(), "chỉ giữ lần yêu cầu xóa đầu tiên");
        assertTrue(loaded.purged());

        // DB cũng chặn giữ pháp lý mà không có lý do
        assertThrows(DataAccessException.class, () -> jdbc.update(
            "UPDATE tenant_privacy SET legal_hold = TRUE, legal_hold_reason = '   ' WHERE tenant_id = :id",
            Map.of("id", TENANT)));
        assertTrue(privacy.find(UUID.randomUUID()).isEmpty());
    }
}
