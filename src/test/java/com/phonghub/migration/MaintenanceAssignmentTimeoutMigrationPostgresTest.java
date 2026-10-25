package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class MaintenanceAssignmentTimeoutMigrationPostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("V53 adds assignment timeout markers and gives existing active assignments a safe grace period")
    void addsTimeoutColumnsAndBackfillsOnlyActiveAssignedTickets() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE maintenance_tickets (
                    id UUID PRIMARY KEY,
                    status VARCHAR(30) NOT NULL,
                    assigned_technician_id UUID,
                    created_at TIMESTAMPTZ NOT NULL
                )
                """);
            statement.execute("""
                INSERT INTO maintenance_tickets (id, status, assigned_technician_id, created_at) VALUES
                    ('00000000-0000-0000-0000-000000000001', 'ASSIGNED',
                     '00000000-0000-0000-0000-000000000010', '2020-01-01T00:00:00Z'),
                    ('00000000-0000-0000-0000-000000000002', 'REPORTED', NULL, '2020-01-01T00:00:00Z'),
                    ('00000000-0000-0000-0000-000000000003', 'RESOLVED',
                     '00000000-0000-0000-0000-000000000010', '2020-01-01T00:00:00Z')
                """);
        }

        new ResourceDatabasePopulator(new ClassPathResource(
            "db/migration/V53__maintenance_assignment_timeout.sql")).execute(dataSource);

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet active = statement.executeQuery("""
                    SELECT assigned_at, understaffed_notified_at FROM maintenance_tickets
                    WHERE id = '00000000-0000-0000-0000-000000000001'
                    """)) {
                assertTrue(active.next());
                assertNotNull(active.getTimestamp("assigned_at"));
                assertNull(active.getTimestamp("understaffed_notified_at"));
            }

            try (ResultSet reported = statement.executeQuery("""
                    SELECT assigned_at, understaffed_notified_at FROM maintenance_tickets
                    WHERE id = '00000000-0000-0000-0000-000000000002'
                    """)) {
                assertTrue(reported.next());
                assertNull(reported.getTimestamp("assigned_at"));
                assertNull(reported.getTimestamp("understaffed_notified_at"));
            }

            try (ResultSet resolved = statement.executeQuery("""
                    SELECT assigned_at, understaffed_notified_at FROM maintenance_tickets
                    WHERE id = '00000000-0000-0000-0000-000000000003'
                    """)) {
                assertTrue(resolved.next());
                assertNull(resolved.getTimestamp("assigned_at"));
                assertNull(resolved.getTimestamp("understaffed_notified_at"));
            }
        }
    }
}
