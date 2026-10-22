package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.SepayGatewayOrderRepositoryPort;
import com.phonghub.domain.model.SepayGatewayOrder;
import com.phonghub.domain.model.SepayGatewayOrder.Status;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresSepayGatewayOrderRepository implements SepayGatewayOrderRepositoryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresSepayGatewayOrderRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private SepayGatewayOrder mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp paidAt = rs.getTimestamp("paid_at");
        return new SepayGatewayOrder(
            rs.getString("order_invoice_number"),
            UUID.fromString(rs.getString("invoice_id")),
            rs.getBigDecimal("amount"),
            Status.valueOf(rs.getString("status")),
            rs.getString("transaction_id"),
            createdAt != null ? createdAt.toInstant() : Instant.now(),
            paidAt != null ? paidAt.toInstant() : null
        );
    }

    @Override
    public Optional<SepayGatewayOrder> findPendingByInvoiceIdForUpdate(UUID invoiceId) {
        String sql = """
            SELECT * FROM sepay_gateway_orders
            WHERE invoice_id = :invoiceId AND status = 'PENDING'
            ORDER BY created_at DESC LIMIT 1 FOR UPDATE
            """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Map.of("invoiceId", invoiceId), this::mapRow));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<SepayGatewayOrder> findByInvoiceNumberForUpdate(String orderInvoiceNumber) {
        String sql = "SELECT * FROM sepay_gateway_orders WHERE order_invoice_number = :orderInvoiceNumber FOR UPDATE";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                sql, Map.of("orderInvoiceNumber", orderInvoiceNumber), this::mapRow
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<SepayGatewayOrder> findByTransactionId(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM sepay_gateway_orders WHERE transaction_id = :transactionId",
                Map.of("transactionId", transactionId), this::mapRow
            ));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void savePending(SepayGatewayOrder order) {
        String sql = """
            INSERT INTO sepay_gateway_orders(order_invoice_number, invoice_id, amount, status, created_at)
            VALUES (:orderInvoiceNumber, :invoiceId, :amount, 'PENDING', :createdAt)
            """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
            .addValue("orderInvoiceNumber", order.orderInvoiceNumber())
            .addValue("invoiceId", order.invoiceId())
            .addValue("amount", order.amount())
            .addValue("createdAt", Timestamp.from(order.createdAt())));
    }

    @Override
    public boolean markPaid(String orderInvoiceNumber, String transactionId, Instant paidAt) {
        String sql = """
            UPDATE sepay_gateway_orders
            SET status = 'PAID', transaction_id = :transactionId, paid_at = :paidAt
            WHERE order_invoice_number = :orderInvoiceNumber AND status = 'PENDING'
            """;
        return jdbcTemplate.update(sql, new MapSqlParameterSource()
            .addValue("orderInvoiceNumber", orderInvoiceNumber)
            .addValue("transactionId", transactionId)
            .addValue("paidAt", Timestamp.from(paidAt))) == 1;
    }

    @Override
    public boolean expire(String orderInvoiceNumber) {
        return jdbcTemplate.update(
            "UPDATE sepay_gateway_orders SET status = 'EXPIRED' WHERE order_invoice_number = :orderInvoiceNumber AND status = 'PENDING'",
            Map.of("orderInvoiceNumber", orderInvoiceNumber)
        ) == 1;
    }
}
