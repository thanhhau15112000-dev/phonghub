package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.InvoicePaymentRecordRepositoryPort;
import com.phonghub.domain.model.InvoicePaymentRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresInvoicePaymentRecordRepository implements InvoicePaymentRecordRepositoryPort {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresInvoicePaymentRecordRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private InvoicePaymentRecord map(ResultSet rs, int row) throws SQLException {
        return new InvoicePaymentRecord(rs.getObject("id", UUID.class), rs.getObject("contract_id", UUID.class),
            rs.getObject("invoice_id", UUID.class), rs.getBigDecimal("amount"),
            InvoicePaymentRecord.Type.valueOf(rs.getString("payment_type")),
            rs.getTimestamp("payment_date").toInstant(), rs.getObject("recorded_by", UUID.class), rs.getString("note"));
    }

    @Override
    public InvoicePaymentRecord save(InvoicePaymentRecord record) {
        jdbc.update("""
            INSERT INTO payments (id, contract_id, invoice_id, amount, payment_type, payment_method, payment_date,
                                 note, status, recorded_by)
            VALUES (:id, :contractId, :invoiceId, :amount, :type, 'DEPOSIT_OFFSET', :paidAt, :note, 'CONFIRMED', :actor)
            """, new MapSqlParameterSource().addValue("id", record.id()).addValue("contractId", record.contractId())
            .addValue("invoiceId", record.invoiceId()).addValue("amount", record.amount())
            .addValue("type", record.type().name()).addValue("paidAt", Timestamp.from(record.paidAt()))
            .addValue("note", record.note()).addValue("actor", record.recordedBy()));
        return record;
    }

    @Override
    public List<InvoicePaymentRecord> findByInvoiceId(UUID invoiceId) {
        return jdbc.query("SELECT id, contract_id, invoice_id, amount, payment_type, payment_date, recorded_by, note "
            + "FROM payments WHERE invoice_id = :invoiceId AND payment_type = 'DEPOSIT_OFFSET' AND status = 'CONFIRMED' "
            + "ORDER BY payment_date, id", Map.of("invoiceId", invoiceId), this::map);
    }

    @Override
    public List<InvoicePaymentRecord> findByContractIds(Collection<UUID> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) return List.of();
        return jdbc.query("SELECT id, contract_id, invoice_id, amount, payment_type, payment_date, recorded_by, note "
            + "FROM payments WHERE contract_id IN (:ids) AND payment_type = 'DEPOSIT_OFFSET' AND status = 'CONFIRMED' "
            + "ORDER BY payment_date DESC, id", Map.of("ids", contractIds), this::map);
    }
}
