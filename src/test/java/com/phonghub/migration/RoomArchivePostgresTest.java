package com.phonghub.migration;

import com.phonghub.application.port.out.RoomRepositoryPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresStatisticsQueryAdapter;
import com.phonghub.adapter.out.security.AesGcmTenantDataProtector;
import com.phonghub.application.port.out.StatisticsQueryPort;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomSearchCriteria;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/** #89 trên Postgres thật: V44 lưu archived_at, danh sách/thống kê chỉ tính phòng đang dùng, số phòng vẫn được giữ. */
@Testcontainers(disabledWithoutDocker = true)
class RoomArchivePostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("archived_at lưu/xóa đúng, tăng version; tìm kiếm, liệt kê và thống kê bỏ qua phòng đã ngừng")
    void archiveRoundTrip() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .load().migrate();
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        JpaTestContext jpa = JpaTestContext.over(ds);
        RoomRepositoryPort rooms = jpa.rooms();
        PostgresStatisticsQueryAdapter stats = new PostgresStatisticsQueryAdapter(jdbc, new AesGcmTenantDataProtector(
            "test-v1", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="));
        UUID propertyId = UUID.fromString(jdbc.getJdbcTemplate().queryForObject(
            "SELECT id::text FROM properties LIMIT 1", String.class));

        Room room = rooms.save(Room.create(propertyId, "Z-ARCHIVE", 9, null, new BigDecimal("3000000"), 2));
        long availableBefore = available(stats, propertyId);
        int activeBefore = rooms.findByPropertyId(propertyId).size();
        long version = room.getVersion();

        Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
        room.archive(at);
        rooms.save(room);
        assertEquals(version + 1, room.getVersion());

        Room loaded = rooms.findById(room.getId()).orElseThrow();
        assertEquals(at, loaded.getArchivedAt());
        assertEquals(activeBefore - 1, rooms.findByPropertyId(propertyId).size());
        assertTrue(rooms.findByPropertyIdAndStatus(propertyId, RoomStatus.AVAILABLE).stream()
            .noneMatch(r -> r.getId().equals(room.getId())));
        assertFalse(rooms.search(RoomSearchCriteria.all(propertyId)).items().stream()
            .anyMatch(r -> r.getId().equals(room.getId())));
        assertEquals(List.of(room.getId()), rooms.search(new RoomSearchCriteria(
            propertyId, null, null, null, null, null, 0, null, true)).items().stream().map(Room::getId).toList());
        assertTrue(rooms.findByPropertyIdAndRoomNumber(propertyId, "z-archive").isPresent(),
            "số phòng vẫn được giữ để không tạo trùng");
        assertEquals(availableBefore - 1, available(stats, propertyId));

        loaded.restore();
        rooms.save(loaded);
        assertNull(rooms.findById(room.getId()).orElseThrow().getArchivedAt());
        assertEquals(activeBefore, rooms.findByPropertyId(propertyId).size());
        assertEquals(availableBefore, available(stats, propertyId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM rooms WHERE id = :id AND archived_at IS NOT NULL",
            Map.of("id", room.getId()), Integer.class));
    }

    private static long available(StatisticsQueryPort stats, UUID propertyId) {
        return stats.roomStatusCounts(List.of(propertyId)).stream()
            .filter(c -> c.status() == RoomStatus.AVAILABLE)
            .mapToLong(StatisticsQueryPort.RoomStatusCount::count).sum();
    }
}
