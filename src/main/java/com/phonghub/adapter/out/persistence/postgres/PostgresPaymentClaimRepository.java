package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.PaymentClaimRepositoryPort;
import com.phonghub.domain.model.PaymentClaim;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresPaymentClaimRepository implements PaymentClaimRepositoryPort {

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresPaymentClaimRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private PaymentClaim map(ResultSet rs, int row) throws SQLException {
        Timestamp decidedAt = rs.getTimestamp("decided_at");
        return new PaymentClaim(rs.getObject("id", UUID.class), rs.getObject("invoice_id", UUID.class),
            rs.getObject("contract_id", UUID.class), rs.getObject("property_id", UUID.class),
            rs.getBigDecimal("amount"), PaymentClaim.Method.valueOf(rs.getString("method")), rs.getString("note"),
            PaymentClaim.Source.valueOf(rs.getString("source")), PaymentClaim.Status.valueOf(rs.getString("status")),
            rs.getObject("created_by", UUID.class), rs.getTimestamp("created_at").toInstant(),
            rs.getObject("decided_by", UUID.class), decidedAt == null ? null : decidedAt.toInstant(),
            rs.getString("reject_reason"));
    }

    @Override
    public PaymentClaim save(PaymentClaim claim) {
        jdbc.update("""
            INSERT INTO payment_claims (id, invoice_id, contract_id, property_id, amount, method, note, source, status,
                created_by, created_at, decided_by, decided_at, reject_reason)
            VALUES (:id, :invoiceId, :contractId, :propertyId, :amount, :method, :note, :source, :status,
                :createdBy, :createdAt, :decidedBy, :decidedAt, :rejectReason)
            ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, decided_by = EXCLUDED.decided_by,
                decided_at = EXCLUDED.decided_at, reject_reason = EXCLUDED.reject_reason
            """, new MapSqlParameterSource()
            .addValue("id", claim.id()).addValue("invoiceId", claim.invoiceId())
            .addValue("contractId", claim.contractId()).addValue("propertyId", claim.propertyId())
            .addValue("amount", claim.amount()).addValue("method", claim.method().name())
            .addValue("note", claim.note()).addValue("source", claim.source().name())
            .addValue("status", claim.status().name()).addValue("createdBy", claim.createdBy())
            .addValue("createdAt", Timestamp.from(claim.createdAt())).addValue("decidedBy", claim.decidedBy())
            .addValue("decidedAt", ts(claim.decidedAt())).addValue("rejectReason", claim.rejectReason()));
        return claim;
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    @Override
    public Optional<PaymentClaim> findById(UUID id) {
        return jdbc.query("SELECT * FROM payment_claims WHERE id = :id", Map.of("id", id), this::map).stream().findFirst();
    }

    @Override
    public Optional<PaymentClaim> findByIdForUpdate(UUID id) {
        return jdbc.query("SELECT * FROM payment_claims WHERE id = :id FOR UPDATE", Map.of("id", id), this::map)
            .stream().findFirst();
    }

    @Override
    public List<PaymentClaim> findByInvoiceId(UUID invoiceId) {
        return jdbc.query("SELECT * FROM payment_claims WHERE invoice_id = :id ORDER BY created_at, id",
            Map.of("id", invoiceId), this::map);
    }

    @Override
    public List<PaymentClaim> findByContractId(UUID contractId) {
        return jdbc.query("SELECT * FROM payment_claims WHERE contract_id = :id ORDER BY created_at DESC, id",
            Map.of("id", contractId), this::map);
    }

    @Override
    public List<PaymentClaim> findPendingByPropertyId(UUID propertyId) {
        return jdbc.query("SELECT * FROM payment_claims WHERE property_id = :id AND status = 'PENDING' "
            + "ORDER BY created_at, id", Map.of("id", propertyId), this::map);
    }
}
