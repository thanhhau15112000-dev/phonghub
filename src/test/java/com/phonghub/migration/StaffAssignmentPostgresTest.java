package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresStaffPropertyAssignmentRepository;
import com.phonghub.domain.model.StaffPropertyAssignment;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #74 trên Postgres thật: phân công staff theo nhà trọ, quyền xem giấy tờ (#70), cập nhật quyền và gỡ phân công. */
@Testcontainers(disabledWithoutDocker = true)
class StaffAssignmentPostgresTest {

    private static final UUID STAFF = UUID.fromString("22222222-2222-2222-2222-222222222221");
    private static final UUID TECHNICIAN = UUID.fromString("33333333-3333-3333-3333-333333333331");
    private static final UUID PROPERTY_Q7 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PROPERTY_TAN_BINH = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("Assignments: V30 backfill, lookups, upsert keeps identity, unassign")
    void staffAssignments() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .load().migrate();

        PostgresStaffPropertyAssignmentRepository assignments =
            new PostgresStaffPropertyAssignmentRepository(new NamedParameterJdbcTemplate(ds));

        // dữ liệu mẫu + V30: staff cũ giữ quyền xem giấy tờ, kỹ thuật viên thì không
        assertEquals(Set.of(PROPERTY_Q7), assignments.findPropertyIdsByUserId(STAFF));
        assertTrue(assignments.find(STAFF, PROPERTY_Q7).orElseThrow().canViewDocuments());
        assertFalse(assignments.find(TECHNICIAN, PROPERTY_Q7).orElseThrow().canViewDocuments());
        assertEquals(Set.of(STAFF, TECHNICIAN), assignments.findByPropertyId(PROPERTY_Q7).stream()
            .map(StaffPropertyAssignment::staffUserId).collect(Collectors.toSet()));
        assertTrue(assignments.isUserAssignedToProperty(STAFF, PROPERTY_Q7));
        assertFalse(assignments.isUserAssignedToProperty(STAFF, PROPERTY_TAN_BINH));
        assertTrue(assignments.find(STAFF, PROPERTY_TAN_BINH).isEmpty());
        assertTrue(assignments.findPropertyIdsByUserId(UUID.randomUUID()).isEmpty());

        // phân công mới: quyền lưu đúng từng cờ
        StaffPropertyAssignment added = new StaffPropertyAssignment(UUID.randomUUID(), STAFF, PROPERTY_TAN_BINH,
            false, true, false, T0);
        assignments.save(added);
        assertEquals(added, assignments.find(STAFF, PROPERTY_TAN_BINH).orElseThrow());
        assertEquals(Set.of(PROPERTY_Q7, PROPERTY_TAN_BINH), assignments.findPropertyIdsByUserId(STAFF));
        assertEquals(2, assignments.findByUserId(STAFF).size());

        // lưu lại cùng staff + nhà trọ: chỉ đổi quyền, giữ id và thời điểm phân công ban đầu
        assignments.save(new StaffPropertyAssignment(UUID.randomUUID(), STAFF, PROPERTY_TAN_BINH,
            true, false, true, T0.plusSeconds(3600)));
        assertEquals(new StaffPropertyAssignment(added.id(), STAFF, PROPERTY_TAN_BINH, true, false, true, T0),
            assignments.find(STAFF, PROPERTY_TAN_BINH).orElseThrow());

        // gỡ phân công chỉ xóa đúng một nhà trọ
        assignments.deleteByUserIdAndPropertyId(STAFF, PROPERTY_TAN_BINH);
        assertFalse(assignments.isUserAssignedToProperty(STAFF, PROPERTY_TAN_BINH));
        assertEquals(Set.of(PROPERTY_Q7), assignments.findPropertyIdsByUserId(STAFF));
    }
}
