package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** V47 trên Postgres thật: phòng còn người ở theo hợp đồng được đưa về OCCUPIED, phòng khác giữ nguyên. */
@Testcontainers(disabledWithoutDocker = true)
class RoomStatusRepairPostgresTest {

    private static final String P101 = "10101010-1010-1010-1010-101010101010";
    private static final String P102 = "10201020-1020-1020-1020-102010201020";
    private static final String P103 = "10301030-1030-1030-1030-103010301030";
    private static final String P104 = "10401040-1040-1040-1040-104010401040";
    private static final String P201 = "20102010-2010-2010-2010-201020102010";
    private static final String TENANT = "44444444-0000-0000-0000-444444444441";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("Phòng có hợp đồng ACTIVE/EXPIRED về OCCUPIED và tăng version; phòng không còn người ở giữ trạng thái")
    void repairsRoomsOfResidingContracts() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .target("46").load().migrate();
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);

        // Dữ liệu lỗi trước bản sửa: P101 có hợp đồng ACTIVE (fixture) nhưng bị chuyển sang bảo trì
        jdbc.update("UPDATE rooms SET status = 'MAINTENANCE' WHERE id = '" + P101 + "'", Map.of());
        // P104 có hợp đồng đã hết hạn nhưng chưa trả phòng, lại bị đánh dấu trống
        jdbc.update("INSERT INTO contracts (id, property_id, room_id, primary_tenant_id, deposit_amount, rent_amount, "
            + "start_date, end_date, payment_day, status) SELECT gen_random_uuid(), property_id, id, '" + TENANT
            + "', 4000000, 4000000, CURRENT_DATE - 400, CURRENT_DATE - 30, 5, 'EXPIRED' FROM rooms WHERE id = '"
            + P104 + "'", Map.of());
        // P201 chỉ có hợp đồng đã kết thúc: không còn người ở
        jdbc.update("INSERT INTO contracts (id, property_id, room_id, primary_tenant_id, deposit_amount, rent_amount, "
            + "start_date, end_date, payment_day, status) SELECT gen_random_uuid(), property_id, id, '" + TENANT
            + "', 3000000, 3000000, CURRENT_DATE - 400, CURRENT_DATE - 30, 5, 'TERMINATED' FROM rooms WHERE id = '"
            + P201 + "'", Map.of());
        long versionBefore = version(jdbc, P101);

        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .load().migrate();

        assertEquals("OCCUPIED", status(jdbc, P101));
        assertEquals(versionBefore + 1, version(jdbc, P101));
        assertEquals("OCCUPIED", status(jdbc, P104));
        assertEquals("MAINTENANCE", status(jdbc, P102), "phòng bảo trì không có hợp đồng giữ nguyên");
        assertEquals("RESERVED", status(jdbc, P103), "phòng đã đặt không có hợp đồng giữ nguyên");
        assertEquals("AVAILABLE", status(jdbc, P201), "hợp đồng đã kết thúc không giữ phòng");
    }

    private static String status(NamedParameterJdbcTemplate jdbc, String roomId) {
        return jdbc.getJdbcTemplate().queryForObject("SELECT status FROM rooms WHERE id = '" + roomId + "'",
            String.class);
    }

    private static long version(NamedParameterJdbcTemplate jdbc, String roomId) {
        return jdbc.getJdbcTemplate().queryForObject("SELECT version FROM rooms WHERE id = '" + roomId + "'",
            Long.class);
    }
}
