package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.InvoiceRepositoryPort;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresInvoiceRepository implements InvoiceRepositoryPort {

    private static final String COLUMNS = """
        id, contract_id, room_id, tenant_id, month, year, rent_amount, total_amount,
        paid_amount, due_date, status, payment_code, invoice_type, ticket_id, paid_at, created_at, updated_at
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresInvoiceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private Invoice mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp paidAt = rs.getTimestamp("paid_at");
        String ticketIdStr = rs.getString("ticket_id");
        return new Invoice(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("contract_id")),
            UUID.fromString(rs.getString("room_id")),
            UUID.fromString(rs.getString("tenant_id")),
            rs.getInt("month"),
            rs.getInt("year"),
            rs.getBigDecimal("rent_amount"),
            rs.getBigDecimal("total_amount"),
            rs.getBigDecimal("paid_amount"),
            rs.getDate("due_date").toLocalDate(),
            InvoiceStatus.valueOf(rs.getString("status")),
            rs.getString("payment_code"),
            InvoiceType.valueOf(rs.getString("invoice_type")),
            ticketIdStr != null ? UUID.fromString(ticketIdStr) : null,
            paidAt != null ? paidAt.toInstant() : null,
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    @Override
    public Invoice save(Invoice invoice) {
        String sql = """
            INSERT INTO invoices (
                id, contract_id, room_id, tenant_id, month, year, rent_amount, utility_amount,
                other_amount, total_amount, paid_amount, due_date, status, payment_code,
                invoice_type, ticket_id, paid_at, created_at, updated_at
            ) VALUES (
                :id, :contractId, :roomId, :tenantId, :month, :year, :rentAmount, 0,
                :otherAmount, :totalAmount, :paidAmount, :dueDate, :status, :paymentCode,
                :invoiceType, :ticketId, :paidAt, :createdAt, :updatedAt
            )
            ON CONFLICT (id) DO UPDATE SET
                paid_amount = EXCLUDED.paid_amount,
                status = EXCLUDED.status,
                paid_at = EXCLUDED.paid_at,
                updated_at = EXCLUDED.updated_at
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", invoice.getId())
            .addValue("contractId", invoice.getContractId())
            .addValue("roomId", invoice.getRoomId())
            .addValue("tenantId", invoice.getTenantId())
            .addValue("month", invoice.getMonth())
            .addValue("year", invoice.getYear())
            .addValue("rentAmount", invoice.getRentAmount())
            .addValue("otherAmount", invoice.getTotalAmount().subtract(invoice.getRentAmount()))
            .addValue("totalAmount", invoice.getTotalAmount())
            .addValue("invoiceType", invoice.getType().name())
            .addValue("ticketId", invoice.getTicketId())
            .addValue("paidAmount", invoice.getPaidAmount())
            .addValue("dueDate", Date.valueOf(invoice.getDueDate()))
            .addValue("status", invoice.getStatus().name())
            .addValue("paymentCode", invoice.getPaymentCode())
            .addValue("paidAt", invoice.getPaidAt() != null ? Timestamp.from(invoice.getPaidAt()) : null)
            .addValue("createdAt", Timestamp.from(invoice.getCreatedAt()))
            .addValue("updatedAt", Timestamp.from(invoice.getUpdatedAt()));

        jdbcTemplate.update(sql, params);
        return invoice;
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        String sql = "SELECT " + COLUMNS + " FROM invoices WHERE id = :id";
        return jdbcTemplate.query(sql, Map.of("id", id), this::mapRow).stream().findFirst();
    }

    @Override
    public List<Invoice> findByContractId(UUID contractId) {
        String sql = "SELECT " + COLUMNS + " FROM invoices WHERE contract_id = :contractId ORDER BY year DESC, month DESC";
        return jdbcTemplate.query(sql, Map.of("contractId", contractId), this::mapRow);
    }

    @Override
    public Optional<Invoice> findActiveByTicketId(UUID ticketId) {
        String sql = "SELECT " + COLUMNS + " FROM invoices WHERE ticket_id = :ticketId AND status <> 'VOIDED'";
        return jdbcTemplate.query(sql, Map.of("ticketId", ticketId), this::mapRow).stream().findFirst();
    }

    @Override
    public Optional<Invoice> findActiveRentByContractIdAndPeriod(UUID contractId, int year, int month) {
        String sql = "SELECT " + COLUMNS + """
             FROM invoices
            WHERE contract_id = :contractId AND year = :year AND month = :month
              AND invoice_type = 'RENT' AND status <> 'VOIDED'
            """;
        return jdbcTemplate.query(
            sql, Map.of("contractId", contractId, "year", year, "month", month), this::mapRow
        ).stream().findFirst();
    }

    @Override
    public Optional<Invoice> findByPaymentCodeForUpdate(String paymentCode) {
        String sql = "SELECT " + COLUMNS + " FROM invoices WHERE payment_code = :code FOR UPDATE";
        return jdbcTemplate.query(sql, Map.of("code", paymentCode), this::mapRow).stream().findFirst();
    }

    @Override
    public boolean existsByPaymentCode(String paymentCode) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM invoices WHERE payment_code = :code", Map.of("code", paymentCode), Integer.class
        );
        return count != null && count > 0;
    }
}
