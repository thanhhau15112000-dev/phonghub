package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.PaymentRepositoryPort;
import com.phonghub.domain.model.PaymentStatus;
import com.phonghub.domain.model.PaymentTransaction;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresPaymentRepository implements PaymentRepositoryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresPaymentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }

    private PaymentTransaction mapRow(ResultSet rs, int rowNum) throws SQLException {
        String contractIdStr = rs.getString("contract_id");
        UUID contractId = contractIdStr != null ? UUID.fromString(contractIdStr) : null;

        return new PaymentTransaction(
            UUID.fromString(rs.getString("id")),
            rs.getLong("sepay_id"),
            rs.getString("gateway"),
            rs.getString("transaction_date"),
            rs.getString("account_number"),
            rs.getString("sub_account"),
            rs.getString("transfer_type"),
            rs.getBigDecimal("transfer_amount"),
            rs.getBigDecimal("accumulated"),
            rs.getString("code"),
            rs.getString("content"),
            rs.getString("reference_code"),
            rs.getString("description"),
            PaymentStatus.valueOf(rs.getString("status")),
            contractId,
            toInstant(rs.getTimestamp("created_at"))
        );
    }

    @Override
    public PaymentTransaction save(PaymentTransaction tx) {
        String sql = """
            INSERT INTO sepay_transactions (
                id, sepay_id, gateway, transaction_date, account_number, sub_account,
                transfer_type, transfer_amount, accumulated, code, content,
                reference_code, description, status, contract_id, created_at
            ) VALUES (
                :id, :sepayId, :gateway, :transactionDate, :accountNumber, :subAccount,
                :transferType, :transferAmount, :accumulated, :code, :content,
                :referenceCode, :description, :status, :contractId, :createdAt
            )
            ON CONFLICT (sepay_id) DO UPDATE SET
                status = EXCLUDED.status,
                contract_id = EXCLUDED.contract_id
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", tx.id())
            .addValue("sepayId", tx.sepayId())
            .addValue("gateway", tx.gateway())
            .addValue("transactionDate", tx.transactionDate())
            .addValue("accountNumber", tx.accountNumber())
            .addValue("subAccount", tx.subAccount())
            .addValue("transferType", tx.transferType())
            .addValue("transferAmount", tx.transferAmount())
            .addValue("accumulated", tx.accumulated())
            .addValue("code", tx.code())
            .addValue("content", tx.content())
            .addValue("referenceCode", tx.referenceCode())
            .addValue("description", tx.description())
            .addValue("status", tx.status().name())
            .addValue("contractId", tx.contractId())
            .addValue("createdAt", Timestamp.from(tx.createdAt()));

        jdbcTemplate.update(sql, params);
        return tx;
    }

    @Override
    public Optional<PaymentTransaction> findById(UUID id) {
        String sql = "SELECT * FROM sepay_transactions WHERE id = :id";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Map.of("id", id), this::mapRow));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<PaymentTransaction> findBySepayId(Long sepayId) {
        if (sepayId == null) {
            return Optional.empty();
        }
        String sql = "SELECT * FROM sepay_transactions WHERE sepay_id = :sepayId";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Map.of("sepayId", sepayId), this::mapRow));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existsBySepayId(Long sepayId) {
        if (sepayId == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM sepay_transactions WHERE sepay_id = :sepayId";
        Integer count = jdbcTemplate.queryForObject(sql, Map.of("sepayId", sepayId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public List<PaymentTransaction> findAll() {
        String sql = "SELECT * FROM sepay_transactions ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, this::mapRow);
    }
}
