package com.phonghub.migration;

import com.phonghub.adapter.out.persistence.postgres.PostgresRoomRepository;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomPage;
import com.phonghub.domain.model.RoomSearchCriteria;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #84: truy vấn lọc + phân trang chạy đúng trên PostgreSQL và dùng index mới. */
@Testcontainers(disabledWithoutDocker = true)
class RoomSearchPostgresTest {

    private static final String MAIN = "classpath:db/migration";
    private static final String FIXTURE = "classpath:db/seed/docker";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static String scalar(Statement st, String sql) throws Exception {
        ResultSet rs = st.executeQuery(sql);
        rs.next();
        return rs.getString(1);
    }

    private List<String> numbers(RoomPage page) {
        return page.items().stream().map(Room::getRoomNumber).toList();
    }

    @Test
    @DisplayName("lọc kết hợp, đếm tổng, phân trang và giới hạn theo danh sách phòng")
    void filtersAndPaging() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).load().migrate();

        UUID propertyId = UUID.randomUUID();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO properties (id, name, address, total_rooms) VALUES ('" + propertyId
                + "', 'Nhà lọc phòng', 'addr', 6)");
            assertEquals("1", scalar(st, "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_rooms_property_filter'"),
                "index lọc phòng phải tồn tại");
        }
        PostgresRoomRepository repo = new PostgresRoomRepository(new NamedParameterJdbcTemplate(ds));
        UUID p103 = null;
        Object[][] data = {
            {"P101", 1, "3000000", RoomStatus.AVAILABLE}, {"P102", 1, "3500000", RoomStatus.OCCUPIED},
            {"P103", 1, "4500000", RoomStatus.AVAILABLE}, {"P201", 2, "4000000", RoomStatus.AVAILABLE},
            {"P202", 2, "5000000", RoomStatus.MAINTENANCE}, {"P301", 3, "3200000", RoomStatus.AVAILABLE},
        };
        for (Object[] row : data) {
            Room room = repo.save(new Room(UUID.randomUUID(), propertyId, (String) row[0], (Integer) row[1], null,
                new BigDecimal((String) row[2]), 2, (RoomStatus) row[3], Instant.now(), Instant.now()));
            if ("P103".equals(row[0])) {
                p103 = room.getId();
            }
        }

        assertEquals(List.of("P101", "P102", "P103", "P201", "P202", "P301"),
            numbers(repo.search(RoomSearchCriteria.all(propertyId))));
        assertEquals(List.of("P101", "P103", "P201", "P301"), numbers(repo.search(new RoomSearchCriteria(
            propertyId, RoomStatus.AVAILABLE, null, null, null, null, 0, null))));
        assertEquals(List.of("P103", "P201"), numbers(repo.search(new RoomSearchCriteria(
            propertyId, RoomStatus.AVAILABLE, new BigDecimal("3800000"), new BigDecimal("4500000"), null, null, 0,
            null))));
        assertEquals(List.of("P201", "P202"), numbers(repo.search(new RoomSearchCriteria(
            propertyId, null, null, null, 2, null, 0, null))));

        RoomPage second = repo.search(new RoomSearchCriteria(propertyId, RoomStatus.AVAILABLE, null, null, null, null,
            1, 3));
        assertEquals(List.of("P301"), numbers(second));
        assertEquals(4, second.totalElements());
        assertEquals(2, second.totalPages());

        RoomPage restricted = repo.search(new RoomSearchCriteria(propertyId, null, null, null, null, List.of(p103), 0,
            null));
        assertEquals(List.of("P103"), numbers(restricted));
        assertTrue(repo.search(new RoomSearchCriteria(propertyId, null, null, null, null, List.of(), 0, null))
            .items().isEmpty());
        assertTrue(repo.search(RoomSearchCriteria.all(UUID.randomUUID())).items().isEmpty());
    }
}
