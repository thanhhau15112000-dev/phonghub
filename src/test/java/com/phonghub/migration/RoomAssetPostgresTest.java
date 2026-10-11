package com.phonghub.migration;

import com.phonghub.adapter.out.persistence.postgres.PostgresRoomAssetEventRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresRoomAssetRepository;
import com.phonghub.domain.model.AssetEventType;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.RoomAsset;
import com.phonghub.domain.model.RoomAssetEvent;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RoomAssetPostgresTest {

    private static final String MAIN = "classpath:db/migration";
    private static final String FIXTURE = "classpath:db/seed/docker";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private PGSimpleDataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    private String firstValue(Statement st, String sql) throws Exception {
        ResultSet rs = st.executeQuery(sql);
        rs.next();
        return rs.getString(1);
    }

    @Test
    @DisplayName("V15 backfills asset codes of legacy rows and the repositories persist assets and events")
    void v15AndRepositories() throws Exception {
        PGSimpleDataSource ds = dataSource();
        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).target("14").load().migrate();

        UUID legacyAssetId = UUID.randomUUID();
        UUID roomId;
        UUID userId;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            roomId = UUID.fromString(firstValue(st, "SELECT id FROM rooms LIMIT 1"));
            userId = UUID.fromString(firstValue(st, "SELECT id FROM users LIMIT 1"));
            st.execute("INSERT INTO room_assets (id, room_id, name, quantity) VALUES ('" + legacyAssetId
                + "', '" + roomId + "', 'Tài sản cũ', 2)");
        }

        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).load().migrate();

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        PostgresRoomAssetRepository assets = new PostgresRoomAssetRepository(jdbc);
        PostgresRoomAssetEventRepository events = new PostgresRoomAssetEventRepository(jdbc);

        RoomAsset legacy = assets.findById(legacyAssetId).orElseThrow();
        assertTrue(legacy.assetCode().startsWith("TS-"));
        assertEquals(AssetStatus.GOOD, legacy.status());
        assertEquals(2, legacy.quantity());

        Instant now = Instant.now();
        RoomAsset asset = new RoomAsset(UUID.randomUUID(), roomId, "QR-0001", "Tivi", "SN1", 1, AssetStatus.GOOD,
            "Mới", "https://example.com/tv.png", new BigDecimal("5000000"), null, now, userId, now, userId);
        assets.save(asset);
        assertTrue(assets.existsByAssetCode("qr-0001"));
        assertThrows(Exception.class, () -> assets.save(new RoomAsset(UUID.randomUUID(), roomId, "QR-0001",
            "Trùng", null, 1, AssetStatus.GOOD, null, null, null, null, now, userId, now, userId)),
            "asset_code must be unique");

        RoomAsset damaged = assets.save(asset.withStatus(AssetStatus.DAMAGED, userId));
        assertEquals(AssetStatus.DAMAGED, assets.findById(asset.id()).orElseThrow().status());

        events.save(new RoomAssetEvent(UUID.randomUUID(), asset.id(), AssetEventType.DAMAGED, "Vỡ màn hình",
            new BigDecimal("300000"), null, now, userId));
        events.save(new RoomAssetEvent(UUID.randomUUID(), asset.id(), AssetEventType.REPAIRED, "Thay màn hình",
            new BigDecimal("1200000"), null, now.plusSeconds(5), userId));
        var history = events.findByAssetId(asset.id());
        assertEquals(2, history.size());
        assertEquals(AssetEventType.REPAIRED, history.getFirst().type());

        assets.save(damaged.softDelete(userId));
        assertFalse(assets.findActiveByRoomId(roomId).stream().anyMatch(a -> a.id().equals(asset.id())));
        assertTrue(assets.findById(asset.id()).orElseThrow().isDeleted());

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            assertEquals("1", firstValue(st, "SELECT count(*) FROM room_asset_events WHERE cost = 1200000 "
                + "AND updated_by IS NOT NULL AND created_by IS NOT NULL"));
        }
    }
}
