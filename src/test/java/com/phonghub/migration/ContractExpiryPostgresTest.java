package com.phonghub.migration;

import com.phonghub.application.port.out.ContractRepositoryPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.adapter.out.persistence.postgres.PostgresContractReminderRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #87 trên Postgres thật: V41 chặn nhắc trùng, hợp đồng hết hạn chưa trả phòng vẫn là hợp đồng của phòng. */
@Testcontainers(disabledWithoutDocker = true)
class ContractExpiryPostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("Reminder is recorded once per end date; an expired contract still belongs to its room")
    void reminderAndExpiredLookup() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .load().migrate();

        UUID contractId;
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT id FROM contracts WHERE status = 'ACTIVE' LIMIT 1");
            rs.next();
            contractId = UUID.fromString(rs.getString(1));
        }

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);

        JpaTestContext jpa = JpaTestContext.over(ds);
        ContractRepositoryPort contracts = jpa.contracts();
        PostgresContractReminderRepository reminders = new PostgresContractReminderRepository(jdbc);
        Contract contract = contracts.findById(contractId).orElseThrow();

        assertTrue(reminders.markExpiryReminder(contractId, contract.getEndDate()));
        assertFalse(reminders.markExpiryReminder(contractId, contract.getEndDate()), "đã nhắc cho ngày kết thúc này");
        assertTrue(reminders.markExpiryReminder(contractId, contract.getEndDate().plusMonths(6)),
            "gia hạn sang ngày kết thúc mới thì được nhắc lại");

        contract.expire();
        contracts.save(contract);
        Contract residing = contracts.findActiveByRoomId(contract.getRoomId()).orElseThrow();
        assertEquals(contractId, residing.getId());
        assertEquals(ContractStatus.EXPIRED, residing.getStatus());

        residing.terminate();
        contracts.save(residing);
        assertTrue(contracts.findActiveByRoomId(contract.getRoomId()).isEmpty());
    }
}
