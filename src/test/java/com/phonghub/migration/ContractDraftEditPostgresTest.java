package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.phonghub.adapter.out.persistence.postgres.PostgresContractRepository;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #89 trên Postgres thật: V45 cho phép CANCELLED, điều khoản nháp và ngày vào ở được lưu lại đúng. */
@Testcontainers(disabledWithoutDocker = true)
class ContractDraftEditPostgresTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    @DisplayName("Sửa nháp lưu đủ điều khoản + ngày vào ở; hủy lưu CANCELLED; DB vẫn chặn trạng thái lạ")
    void draftEditAndCancelRoundTrip() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration", "classpath:db/seed/docker")
            .load().migrate();
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ds);
        PostgresContractRepository contracts = new PostgresContractRepository(jdbc);
        Map<String, Object> room = jdbc.queryForMap("SELECT id, property_id FROM rooms LIMIT 1", Map.of());
        UUID tenantId = UUID.fromString(jdbc.getJdbcTemplate().queryForObject("SELECT id::text FROM tenants LIMIT 1",
            String.class));

        LocalDate start = LocalDate.of(2026, 12, 1);
        Contract draft = contracts.save(Contract.create((UUID) room.get("property_id"), (UUID) room.get("id"), tenantId,
            new BigDecimal("1000000"), new BigDecimal("3000000"), start, start.plusYears(1), 5));

        Contract loaded = contracts.findById(draft.getId()).orElseThrow();
        LocalDate newStart = start.plusDays(14);
        loaded.updateDraftTerms(new BigDecimal("1500000"), new BigDecimal("3200000"), newStart, newStart.plusMonths(6), 9);
        contracts.save(loaded);

        Contract edited = contracts.findById(draft.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("1500000").compareTo(edited.getDepositAmount()));
        assertEquals(0, new BigDecimal("3200000").compareTo(edited.getRentAmount()));
        assertEquals(newStart, edited.getStartDate());
        assertEquals(newStart.plusMonths(6), edited.getEndDate());
        assertEquals(9, edited.getPaymentDay());
        assertEquals(newStart, edited.getOccupants().getFirst().checkInDate());
        assertEquals(draft.getVersion() + 1, edited.getVersion());

        edited.cancelDraft();
        contracts.save(edited);
        assertEquals(ContractStatus.CANCELLED, contracts.findById(draft.getId()).orElseThrow().getStatus());

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "UPDATE contracts SET status = 'DELETED' WHERE id = :id", Map.of("id", draft.getId())));
    }
}
