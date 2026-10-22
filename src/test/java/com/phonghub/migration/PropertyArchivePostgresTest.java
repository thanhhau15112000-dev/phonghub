package com.phonghub.migration;

import com.phonghub.application.port.out.PropertyRepositoryPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresStatisticsQueryAdapter;
import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.application.port.out.StatisticsQueryPort;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #89 trên Postgres thật: V43 lưu/đọc archived_at, nhà trọ đã ẩn không tính vào thống kê và danh sách chờ duyệt. */
@Testcontainers(disabledWithoutDocker = true)
class PropertyArchivePostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("archived_at được lưu và xóa đúng; thống kê admin bỏ qua nhà trọ đã ẩn")
    void archiveRoundTrip() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .load().migrate();
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        JpaTestContext jpa = JpaTestContext.over(ds);
        PropertyRepositoryPort properties = jpa.properties();
        PostgresStatisticsQueryAdapter stats = new PostgresStatisticsQueryAdapter(jdbc, new AesGcmTenantDataProtector(
            "test-v1", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="));

        Property pending = properties.save(new Property(UUID.randomUUID(), "Nhà chờ duyệt", "9 Hai Bà Trưng", null, 3,
            null, PropertyApprovalStatus.PENDING, null, Instant.now()));
        long pendingBefore = pendingCount(stats);
        assertTrue(stats.pendingProperties().stream().anyMatch(r -> r.id().equals(pending.id())));
        assertNull(properties.findById(pending.id()).orElseThrow().archivedAt());

        Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
        properties.save(pending.archive(at));
        Property loaded = properties.findById(pending.id()).orElseThrow();
        assertEquals(at, loaded.archivedAt());
        assertTrue(properties.findAll().stream().anyMatch(p -> p.id().equals(pending.id()) && p.isArchived()),
            "repository vẫn trả nhà đã ẩn; service quyết định ẩn khỏi danh sách");
        assertFalse(stats.pendingProperties().stream().anyMatch(r -> r.id().equals(pending.id())));
        assertEquals(pendingBefore - 1, pendingCount(stats));

        properties.save(loaded.restore());
        assertNull(properties.findById(pending.id()).orElseThrow().archivedAt());
        assertEquals(pendingBefore, pendingCount(stats));
    }

    private static long pendingCount(StatisticsQueryPort stats) {
        return stats.propertyApprovalCounts().stream()
            .filter(c -> c.status() == PropertyApprovalStatus.PENDING)
            .mapToLong(StatisticsQueryPort.ApprovalCount::count).sum();
    }
}
