package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class MaintenancePriorityCategoriesMigrationPostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("V63 maps URGENT and null to current categories without changing existing SLA deadlines")
    void migratesLegacyPrioritiesAndPreservesDueAt() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE maintenance_tickets (
                    id INTEGER PRIMARY KEY,
                    priority VARCHAR(30),
                    due_at TIMESTAMPTZ,
                    CONSTRAINT maintenance_tickets_priority_check
                        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
                )
                """);
            statement.execute("""
                INSERT INTO maintenance_tickets (id, priority, due_at) VALUES
                    (1, 'URGENT', '2026-01-01T04:00:00Z'),
                    (2, NULL, NULL)
                """);
        }

        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V63__maintenance_priority_categories.sql"))
            .execute(dataSource);

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet urgent = statement.executeQuery("SELECT priority, due_at FROM maintenance_tickets WHERE id = 1")) {
                assertTrue(urgent.next());
                assertEquals("HIGH", urgent.getString("priority"));
                assertNotNull(urgent.getTimestamp("due_at"));
                assertEquals(Instant.parse("2026-01-01T04:00:00Z"), urgent.getTimestamp("due_at").toInstant());
            }
            try (ResultSet missing = statement.executeQuery("SELECT priority FROM maintenance_tickets WHERE id = 2")) {
                assertTrue(missing.next());
                assertEquals("UNCLASSIFIED", missing.getString("priority"));
            }
            try (ResultSet defaults = statement.executeQuery("""
                    SELECT column_default, is_nullable
                    FROM information_schema.columns
                    WHERE table_name = 'maintenance_tickets' AND column_name = 'priority'
                    """)) {
                assertTrue(defaults.next());
                assertEquals("'UNCLASSIFIED'::character varying", defaults.getString("column_default"));
                assertEquals("NO", defaults.getString("is_nullable"));
            }
        }
    }
}
