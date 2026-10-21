package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.adapter.out.security.PersonalDataFields;
import com.phonghub.application.port.out.StatisticsQueryPort;
import com.phonghub.application.port.out.TenantDataProtectionPort;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Thống kê bằng truy vấn GROUP BY trên PostgreSQL (#93). Mỗi phương thức là một câu SQL cho cả phạm vi nhà trọ,
 * dùng các index ở V39.
 */
public class PostgresStatisticsQueryAdapter implements StatisticsQueryPort {

    private static final String UNPAID = "('ISSUED', 'PARTIALLY_PAID', 'OVERDUE')";
    private static final String LOCAL_TIME = " AT TIME ZONE 'Asia/Ho_Chi_Minh'";

    private final NamedParameterJdbcTemplate jdbc;
    private final TenantDataProtectionPort protector;

    public PostgresStatisticsQueryAdapter(NamedParameterJdbcTemplate jdbc, TenantDataProtectionPort protector) {
        this.jdbc = jdbc;
        this.protector = protector;
    }

    @Override
    public List<InvoiceAggregate> invoiceAggregates(
        Collection<UUID> propertyIds, YearMonth from, YearMonth to, LocalDate today
    ) {
        if (isEmptyScope(propertyIds)) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("fromYear", from.getYear()).addValue("fromMonth", from.getMonthValue())
            .addValue("toYear", to.getYear()).addValue("toMonth", to.getMonthValue())
            .addValue("today", Date.valueOf(today));
        String sql = """
            SELECT c.property_id, i.year, i.month,
                   COUNT(*) AS invoice_count,
                   COALESCE(SUM(i.total_amount), 0) AS billed,
                   COALESCE(SUM(i.paid_amount), 0) AS collected,
                   COALESCE(SUM(GREATEST(i.total_amount - i.paid_amount, 0))
                       FILTER (WHERE i.status IN %1$s), 0) AS outstanding,
                   COUNT(*) FILTER (WHERE i.status IN %1$s AND i.due_date < :today) AS overdue_count,
                   COALESCE(SUM(GREATEST(i.total_amount - i.paid_amount, 0))
                       FILTER (WHERE i.status IN %1$s AND i.due_date < :today), 0) AS overdue_amount
            FROM invoices i
            JOIN contracts c ON c.id = i.contract_id
            WHERE i.status <> 'VOIDED'
              AND i.consolidated_into_invoice_id IS NULL
              AND (i.year, i.month) BETWEEN (:fromYear, :fromMonth) AND (:toYear, :toMonth)
            """.formatted(UNPAID)
            + propertyFilter("c.property_id", propertyIds, params)
            + " GROUP BY c.property_id, i.year, i.month";
        return jdbc.query(sql, params, (rs, n) -> new InvoiceAggregate(
            UUID.fromString(rs.getString("property_id")),
            YearMonth.of(rs.getInt("year"), rs.getInt("month")),
            rs.getLong("invoice_count"),
            rs.getBigDecimal("billed"),
            rs.getBigDecimal("collected"),
            rs.getBigDecimal("outstanding"),
            rs.getLong("overdue_count"),
            rs.getBigDecimal("overdue_amount")));
    }

    @Override
    public List<RoomStatusCount> roomStatusCounts(Collection<UUID> propertyIds) {
        if (isEmptyScope(propertyIds)) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource();
        String sql = "SELECT property_id, status, COUNT(*) AS cnt FROM rooms WHERE archived_at IS NULL"
            + propertyFilter("property_id", propertyIds, params)
            + " GROUP BY property_id, status";
        return jdbc.query(sql, params, (rs, n) -> new RoomStatusCount(
            UUID.fromString(rs.getString("property_id")),
            RoomStatus.valueOf(rs.getString("status")),
            rs.getLong("cnt")));
    }

    @Override
    public List<MaintenanceAggregate> completedMaintenance(Collection<UUID> propertyIds, Instant from, Instant to) {
        if (isEmptyScope(propertyIds)) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", Timestamp.from(from))
            .addValue("to", Timestamp.from(to));
        String sql = """
            WITH done AS (
                SELECT ticket_id, MIN(created_at) AS done_at
                FROM maintenance_ticket_events
                WHERE to_status IN ('RESOLVED', 'AWAITING_PAYMENT')
                GROUP BY ticket_id
            )
            SELECT r.property_id,
                   EXTRACT(YEAR FROM d.done_at%1$s)::int AS y,
                   EXTRACT(MONTH FROM d.done_at%1$s)::int AS m,
                   COUNT(*) AS completed_count,
                   COALESCE(SUM(t.repair_cost) FILTER (WHERE t.liable_party <> 'TENANT'), 0) AS owner_cost,
                   COALESCE(SUM(EXTRACT(EPOCH FROM (d.done_at - t.created_at)) / 3600.0), 0) AS total_hours
            FROM done d
            JOIN maintenance_tickets t ON t.id = d.ticket_id
            JOIN rooms r ON r.id = t.room_id
            WHERE d.done_at >= :from AND d.done_at < :to
            """.formatted(LOCAL_TIME)
            + propertyFilter("r.property_id", propertyIds, params)
            + " GROUP BY r.property_id, y, m";
        return jdbc.query(sql, params, (rs, n) -> new MaintenanceAggregate(
            UUID.fromString(rs.getString("property_id")),
            YearMonth.of(rs.getInt("y"), rs.getInt("m")),
            rs.getLong("completed_count"),
            rs.getBigDecimal("owner_cost"),
            rs.getDouble("total_hours")));
    }

    @Override
    public List<OpenMaintenanceCount> openMaintenance(Collection<UUID> propertyIds, Instant now) {
        if (isEmptyScope(propertyIds)) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("now", Timestamp.from(now));
        String sql = """
            SELECT r.property_id, COUNT(*) AS open_count, COUNT(*) FILTER (WHERE t.due_at < :now) AS overdue_count
            FROM maintenance_tickets t
            JOIN rooms r ON r.id = t.room_id
            WHERE t.status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS')
            """
            + propertyFilter("r.property_id", propertyIds, params)
            + " GROUP BY r.property_id";
        return jdbc.query(sql, params, (rs, n) -> new OpenMaintenanceCount(
            UUID.fromString(rs.getString("property_id")),
            rs.getLong("open_count"),
            rs.getLong("overdue_count")));
    }

    @Override
    public List<ApprovalCount> propertyApprovalCounts() {
        return jdbc.query("SELECT approval_status, COUNT(*) AS cnt FROM properties WHERE archived_at IS NULL GROUP BY approval_status",
            new MapSqlParameterSource(), (rs, n) -> new ApprovalCount(
                PropertyApprovalStatus.fromString(rs.getString("approval_status")), rs.getLong("cnt")));
    }

    @Override
    public List<PendingPropertyRow> pendingProperties() {
        String sql = """
            SELECT p.id, p.name, p.address, p.owner_id, p.created_at, u.full_name, u.email
            FROM properties p
            LEFT JOIN users u ON u.id = p.owner_id
            WHERE p.approval_status = 'PENDING' AND p.archived_at IS NULL
            ORDER BY p.created_at, p.name
            """;
        return jdbc.query(sql, new MapSqlParameterSource(), (rs, n) -> {
            String ownerId = rs.getString("owner_id");
            Timestamp createdAt = rs.getTimestamp("created_at");
            return new PendingPropertyRow(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                rs.getString("address"),
                ownerId == null ? null : UUID.fromString(ownerId),
                protector.decryptField(PersonalDataFields.USERS_FULL_NAME, rs.getString("full_name")),
                protector.decryptField(PersonalDataFields.USERS_EMAIL, rs.getString("email")),
                createdAt == null ? null : createdAt.toInstant());
        });
    }

    @Override
    public List<UserCount> userCounts() {
        return jdbc.query("SELECT role, status, COUNT(*) AS cnt FROM users GROUP BY role, status",
            new MapSqlParameterSource(), (rs, n) -> new UserCount(
                UserRole.valueOf(rs.getString("role")),
                User.UserStatus.valueOf(rs.getString("status")),
                rs.getLong("cnt")));
    }

    @Override
    public List<SignupCount> userSignups(Instant from, Instant to) {
        String sql = """
            SELECT EXTRACT(YEAR FROM created_at%1$s)::int AS y,
                   EXTRACT(MONTH FROM created_at%1$s)::int AS m,
                   role, COUNT(*) AS cnt
            FROM users
            WHERE created_at >= :from AND created_at < :to
            GROUP BY y, m, role
            """.formatted(LOCAL_TIME);
        return jdbc.query(sql, range(from, to), (rs, n) -> new SignupCount(
            YearMonth.of(rs.getInt("y"), rs.getInt("m")),
            UserRole.valueOf(rs.getString("role")),
            rs.getLong("cnt")));
    }

    @Override
    public List<ActivityCount> auditActivity(Instant from, Instant to) {
        String sql = """
            SELECT EXTRACT(YEAR FROM created_at%1$s)::int AS y,
                   EXTRACT(MONTH FROM created_at%1$s)::int AS m,
                   COUNT(*) AS actions,
                   COUNT(DISTINCT actor_id) AS active_users
            FROM audit_logs
            WHERE created_at >= :from AND created_at < :to
            GROUP BY y, m
            """.formatted(LOCAL_TIME);
        return jdbc.query(sql, range(from, to), (rs, n) -> new ActivityCount(
            YearMonth.of(rs.getInt("y"), rs.getInt("m")),
            rs.getLong("actions"),
            rs.getLong("active_users")));
    }

    private static boolean isEmptyScope(Collection<UUID> propertyIds) {
        return propertyIds != null && propertyIds.isEmpty();
    }

    private static String propertyFilter(String column, Collection<UUID> propertyIds, MapSqlParameterSource params) {
        if (propertyIds == null) {
            return "";
        }
        params.addValue("propertyIds", propertyIds);
        return " AND " + column + " IN (:propertyIds)";
    }

    private static MapSqlParameterSource range(Instant from, Instant to) {
        return new MapSqlParameterSource()
            .addValue("from", Timestamp.from(from))
            .addValue("to", Timestamp.from(to));
    }
}
