package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.domain.exception.OptimisticLockConflictException;
import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractOccupant;
import com.phonghub.domain.model.ContractStatus;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class PostgresContractRepository implements ContractRepositoryPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresContractRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }

    private static LocalDate toLocalDate(Date date) {
        return date != null ? date.toLocalDate() : null;
    }

    private record ContractRow(
        UUID id,
        UUID propertyId,
        UUID roomId,
        UUID primaryTenantId,
        java.math.BigDecimal depositAmount,
        java.math.BigDecimal rentAmount,
        LocalDate startDate,
        LocalDate endDate,
        int paymentDay,
        ContractStatus status,
        Instant activatedAt,
        java.math.BigDecimal depositAppliedAmount,
        java.math.BigDecimal settlementCreditAmount,
        Instant createdAt,
        Instant updatedAt,
        long version
    ) {
        Contract toContract(List<ContractOccupant> occupants) {
            Contract contract = new Contract(
                id, propertyId, roomId, primaryTenantId, depositAmount, rentAmount,
                startDate, endDate, paymentDay, status, occupants, activatedAt,
                depositAppliedAmount, settlementCreditAmount, createdAt, updatedAt
            );
            contract.restoreVersion(version);
            return contract;
        }
    }

    private ContractRow mapContractRow(ResultSet rs, int rowNum) throws SQLException {
        return new ContractRow(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("property_id")),
            UUID.fromString(rs.getString("room_id")),
            UUID.fromString(rs.getString("primary_tenant_id")),
            rs.getBigDecimal("deposit_amount"),
            rs.getBigDecimal("rent_amount"),
            toLocalDate(rs.getDate("start_date")),
            toLocalDate(rs.getDate("end_date")),
            rs.getInt("payment_day"),
            ContractStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("activated_at") != null ? rs.getTimestamp("activated_at").toInstant() : null,
            rs.getBigDecimal("deposit_applied_amount"),
            rs.getBigDecimal("settlement_credit_amount"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at")),
            rs.getLong("version")
        );
    }

    private Map<UUID, List<ContractOccupant>> loadOccupantsBatch(List<UUID> contractIds) {
        if (contractIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String sql = "SELECT * FROM contract_occupants WHERE contract_id IN (:contractIds) ORDER BY is_primary DESC, check_in_date ASC";
        Map<UUID, List<ContractOccupant>> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, Map.of("contractIds", contractIds), (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            ContractOccupant occ = new ContractOccupant(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("contract_id")),
                UUID.fromString(rs.getString("tenant_id")),
                rs.getBoolean("is_primary"),
                toLocalDate(rs.getDate("check_in_date")),
                toLocalDate(rs.getDate("check_out_date"))
            );
            result.computeIfAbsent(occ.contractId(), ignored -> new ArrayList<>()).add(occ);
        });
        return result;
    }

    private List<Contract> hydrate(List<ContractRow> rows) {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> ids = rows.stream().map(ContractRow::id).toList();
        Map<UUID, List<ContractOccupant>> occupantsByContract = loadOccupantsBatch(ids);
        return rows.stream()
            .map(row -> row.toContract(occupantsByContract.getOrDefault(row.id(), Collections.emptyList())))
            .toList();
    }

    @Override
    public Contract save(Contract contract) {
        String sql = """
            INSERT INTO contracts (id, property_id, room_id, primary_tenant_id, deposit_amount, rent_amount, start_date, end_date, payment_day, status, activated_at, deposit_applied_amount, settlement_credit_amount, created_at, updated_at, version)
            VALUES (:id, :propertyId, :roomId, :primaryTenantId, :depositAmount, :rentAmount, :startDate, :endDate, :paymentDay, :status, :activatedAt, :depositAppliedAmount, :settlementCreditAmount, :createdAt, :updatedAt, :version)
            ON CONFLICT (id) DO UPDATE SET
                property_id = EXCLUDED.property_id,
                room_id = EXCLUDED.room_id,
                primary_tenant_id = EXCLUDED.primary_tenant_id,
                deposit_amount = EXCLUDED.deposit_amount,
                rent_amount = EXCLUDED.rent_amount,
                start_date = EXCLUDED.start_date,
                end_date = EXCLUDED.end_date,
                payment_day = EXCLUDED.payment_day,
                status = EXCLUDED.status,
                activated_at = EXCLUDED.activated_at,
                deposit_applied_amount = EXCLUDED.deposit_applied_amount,
                settlement_credit_amount = EXCLUDED.settlement_credit_amount,
                updated_at = EXCLUDED.updated_at,
                version = contracts.version + 1
            WHERE contracts.version = :version
            RETURNING version
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", contract.getId())
            .addValue("version", contract.getVersion())
            .addValue("propertyId", contract.getPropertyId())
            .addValue("roomId", contract.getRoomId())
            .addValue("primaryTenantId", contract.getPrimaryTenantId())
            .addValue("depositAmount", contract.getDepositAmount())
            .addValue("rentAmount", contract.getRentAmount())
            .addValue("startDate", Date.valueOf(contract.getStartDate()))
            .addValue("endDate", Date.valueOf(contract.getEndDate()))
            .addValue("paymentDay", contract.getPaymentDay())
            .addValue("status", contract.getStatus().name())
            .addValue("activatedAt", contract.getActivatedAt() != null ? Timestamp.from(contract.getActivatedAt()) : null)
            .addValue("depositAppliedAmount", contract.getDepositAppliedAmount())
            .addValue("settlementCreditAmount", contract.getSettlementCreditAmount())
            .addValue("createdAt", Timestamp.from(contract.getCreatedAt()))
            .addValue("updatedAt", Timestamp.from(contract.getUpdatedAt()));

        List<Long> versions = jdbcTemplate.queryForList(sql, params, Long.class);
        if (versions.isEmpty()) {
            throw new OptimisticLockConflictException("Hợp đồng");
        }
        contract.restoreVersion(versions.getFirst());

        // Sync occupants
        jdbcTemplate.update("DELETE FROM contract_occupants WHERE contract_id = :contractId", Map.of("contractId", contract.getId()));
        String insertOccupantSql = """
            INSERT INTO contract_occupants (id, contract_id, tenant_id, is_primary, check_in_date, check_out_date)
            VALUES (:id, :contractId, :tenantId, :isPrimary, :checkInDate, :checkOutDate)
            """;

        for (ContractOccupant o : contract.getOccupants()) {
            MapSqlParameterSource occParams = new MapSqlParameterSource()
                .addValue("id", o.id())
                .addValue("contractId", o.contractId())
                .addValue("tenantId", o.tenantId())
                .addValue("isPrimary", o.isPrimary())
                .addValue("checkInDate", Date.valueOf(o.checkInDate()))
                .addValue("checkOutDate", o.checkOutDate() != null ? Date.valueOf(o.checkOutDate()) : null);
            jdbcTemplate.update(insertOccupantSql, occParams);
        }

        return contract;
    }

    @Override
    public Optional<Contract> findById(UUID id) {
        String sql = "SELECT * FROM contracts WHERE id = :id";
        List<Contract> list = hydrate(jdbcTemplate.query(sql, Map.of("id", id), this::mapContractRow));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public Optional<Contract> findByIdForUpdate(UUID id) {
        String sql = "SELECT * FROM contracts WHERE id = :id FOR UPDATE";
        List<Contract> list = hydrate(jdbcTemplate.query(sql, Map.of("id", id), this::mapContractRow));
        return list.stream().findFirst();
    }

    @Override
    public List<Contract> findByPropertyId(UUID propertyId) {
        String sql = "SELECT * FROM contracts WHERE property_id = :propertyId ORDER BY created_at DESC";
        return hydrate(jdbcTemplate.query(sql, Map.of("propertyId", propertyId), this::mapContractRow));
    }

    @Override
    public List<Contract> findByRoomId(UUID roomId) {
        String sql = "SELECT * FROM contracts WHERE room_id = :roomId ORDER BY created_at DESC";
        return hydrate(jdbcTemplate.query(sql, Map.of("roomId", roomId), this::mapContractRow));
    }

    @Override
    public Optional<Contract> findActiveByRoomId(UUID roomId) {
        String sql = """
            SELECT * FROM contracts WHERE room_id = :roomId AND status IN ('ACTIVE', 'EXPIRED')
            ORDER BY (status = 'ACTIVE') DESC, updated_at DESC LIMIT 1
            """;
        List<Contract> list = hydrate(jdbcTemplate.query(sql, Map.of("roomId", roomId), this::mapContractRow));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    @Override
    public List<Contract> findByPrimaryTenantId(UUID tenantId) {
        String sql = "SELECT * FROM contracts WHERE primary_tenant_id = :tenantId ORDER BY created_at DESC";
        return hydrate(jdbcTemplate.query(sql, Map.of("tenantId", tenantId), this::mapContractRow));
    }

    @Override
    public List<Contract> findByOccupantTenantId(UUID tenantId) {
        String sql = """
            SELECT c.* FROM contracts c
            JOIN contract_occupants co ON c.id = co.contract_id
            WHERE co.tenant_id = :tenantId
              AND (co.check_out_date IS NULL OR co.check_out_date > CURRENT_DATE)
            ORDER BY c.created_at DESC
            """;
        return hydrate(jdbcTemplate.query(sql, Map.of("tenantId", tenantId), this::mapContractRow));
    }

    @Override
    public List<Contract> findAll() {
        String sql = "SELECT * FROM contracts ORDER BY created_at DESC";
        return hydrate(jdbcTemplate.query(sql, this::mapContractRow));
    }
}
