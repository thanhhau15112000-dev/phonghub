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
class MaintenanceSlaMigrationPostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("V29 backfills open tickets without touching legacy rejected rows")
    void backfillsOnlyOpenTicketsWhenLegacyRejectedRowViolatesV24Constraint() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            statement.execute("""
                CREATE TABLE maintenance_tickets (
                    id UUID PRIMARY KEY,
                    status VARCHAR(30) NOT NULL,
                    priority VARCHAR(30) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL
                )
                """);
            statement.execute("""
                INSERT INTO maintenance_tickets (id, status, priority, created_at) VALUES
                    ('00000000-0000-0000-0000-000000000001', 'REJECTED', 'HIGH', '2020-01-01T00:00:00Z'),
                    ('00000000-0000-0000-0000-000000000002', 'REPORTED', 'MEDIUM', '2020-01-01T00:00:00Z')
                """);
        }

        new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V24__maintenance_ticket_reject_reason.sql"),
            new ClassPathResource("db/migration/V29__maintenance_sla_timeline.sql")
        ).execute(dataSource);

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet rejected = statement.executeQuery("""
                    SELECT reject_reason, due_at, sla_notified_at
                    FROM maintenance_tickets
                    WHERE id = '00000000-0000-0000-0000-000000000001'
                    """)) {
                assertTrue(rejected.next());
                assertNull(rejected.getString("reject_reason"));
                assertNull(rejected.getTimestamp("due_at"));
                assertNull(rejected.getTimestamp("sla_notified_at"));
            }

            try (ResultSet open = statement.executeQuery("""
                    SELECT due_at, sla_notified_at
                    FROM maintenance_tickets
                    WHERE id = '00000000-0000-0000-0000-000000000002'
                    """)) {
                assertTrue(open.next());
                assertNotNull(open.getTimestamp("due_at"));
                assertNotNull(open.getTimestamp("sla_notified_at"));
            }
        }
    }
}
