package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.InvoiceReminderPort;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresInvoiceReminderRepository implements InvoiceReminderPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresInvoiceReminderRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean markDueReminder(UUID invoiceId, LocalDate dueDate) {
        return jdbcTemplate.update("""
            INSERT INTO invoice_due_reminders (invoice_id, due_date) VALUES (:invoiceId, :dueDate)
            ON CONFLICT (invoice_id, due_date) DO NOTHING
            """, new MapSqlParameterSource()
            .addValue("invoiceId", invoiceId)
            .addValue("dueDate", Date.valueOf(dueDate))) == 1;
    }
}
