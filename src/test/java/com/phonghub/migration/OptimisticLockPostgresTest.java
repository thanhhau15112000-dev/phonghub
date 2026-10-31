package com.phonghub.migration;

import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.adapter.out.persistence.postgres.PostgresInvoiceRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresMaintenanceTicketRepository;
import com.phonghub.domain.exception.OptimisticLockConflictException;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Room;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.YearMonth;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** #76: hai bản đọc cùng một bản ghi, bản lưu sau phải nhận xung đột thay vì ghi đè. */
@Testcontainers(disabledWithoutDocker = true)
class OptimisticLockPostgresTest {

    private static final String MAIN = "classpath:db/migration";
    private static final String FIXTURE = "classpath:db/seed/docker";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private PGSimpleDataSource migrated() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).load().migrate();
        return ds;
    }

    private UUID firstId(PGSimpleDataSource ds, String table) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT id FROM " + table + " LIMIT 1");
            return rs.next() ? UUID.fromString(rs.getString(1)) : null;
        }
    }

    private <T> void assertSecondSaveConflicts(
        UUID id,
        Function<UUID, T> load,
        UnaryOperator<T> save,
        Function<T, Long> version
    ) {
        T first = load.apply(id);
        T second = load.apply(id);
        assertEquals(version.apply(first), version.apply(second));
        long before = version.apply(first);

        save.apply(first);
        assertEquals(before + 1, version.apply(first));
        assertThrows(OptimisticLockConflictException.class, () -> save.apply(second));

        T reloaded = load.apply(id);
        assertEquals(before + 1, version.apply(reloaded));
        save.apply(reloaded);
        assertEquals(before + 2, version.apply(reloaded));
    }

    @Test
    @DisplayName("room: bản lưu sau nhận xung đột; lưu lại bản mới tải thì thành công")
    void roomConflict() throws Exception {
        PGSimpleDataSource ds = migrated();
        RoomRepositoryPort repo = JpaTestContext.over(ds).rooms();
        assertSecondSaveConflicts(firstId(ds, "rooms"), id -> repo.findById(id).orElseThrow(), repo::save, Room::getVersion);
    }

    @Test
    @DisplayName("contract: bản lưu sau nhận xung đột")
    void contractConflict() throws Exception {
        PGSimpleDataSource ds = migrated();
        UUID id = firstId(ds, "contracts");
        assumeTrue(id != null, "seed has no contract");
        ContractRepositoryPort repo = JpaTestContext.over(ds).contracts();
        assertSecondSaveConflicts(id, x -> repo.findById(x).orElseThrow(), repo::save, Contract::getVersion);
    }

    @Test
    @DisplayName("invoice: lưu mới có version 0, bản lưu sau nhận xung đột")
    void invoiceConflict() throws Exception {
        PGSimpleDataSource ds = migrated();
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        JpaTestContext jpa = JpaTestContext.over(ds);
        Contract contract = jpa.contracts().findById(firstId(ds, "contracts")).orElseThrow();
        assumeTrue(contract.isActive(), "seed contract is not active");
        PostgresInvoiceRepository repo = new PostgresInvoiceRepository(jdbc);
        Invoice created = repo.save(Invoice.issueMonthlyRent(
            contract, YearMonth.from(contract.getStartDate()), "PHTEST0001"));
        assertEquals(0L, created.getVersion());
        assertSecondSaveConflicts(created.getId(), x -> repo.findById(x).orElseThrow(), repo::save, Invoice::getVersion);
    }

    @Test
    @DisplayName("maintenance ticket: bản lưu sau nhận xung đột")
    void ticketConflict() throws Exception {
        PGSimpleDataSource ds = migrated();
        UUID id = firstId(ds, "maintenance_tickets");
        assumeTrue(id != null, "seed has no ticket");
        PostgresMaintenanceTicketRepository repo = new PostgresMaintenanceTicketRepository(new NamedParameterJdbcTemplate(ds));
        assertSecondSaveConflicts(id, x -> repo.findById(x).orElseThrow(), repo::save, MaintenanceTicket::getVersion);
    }
}
