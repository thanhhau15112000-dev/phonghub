package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.ContractReminderPort;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresContractReminderRepository implements ContractReminderPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresContractReminderRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean markExpiryReminder(UUID contractId, LocalDate endDate) {
        return jdbcTemplate.update("""
            INSERT INTO contract_expiry_reminders (contract_id, end_date) VALUES (:contractId, :endDate)
            ON CONFLICT (contract_id, end_date) DO NOTHING
            """, new MapSqlParameterSource()
            .addValue("contractId", contractId)
            .addValue("endDate", Date.valueOf(endDate))) == 1;
    }
}
