package com.phonghub.adapter.out.persistence.postgres;

import com.phonghub.application.port.out.InvoiceAnomalyQueryPort;
import com.phonghub.domain.model.MeterType;
import java.sql.Array;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Truy vấn dữ liệu cho phát hiện bất thường hóa đơn (#156): mỗi phương thức một câu SQL cho cả phạm vi nhà trọ. */
public class PostgresInvoiceAnomalyQueryAdapter implements InvoiceAnomalyQueryPort {

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresInvoiceAnomalyQueryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UtilityReading> utilityReadings(Collection<UUID> propertyIds, YearMonth from, YearMonth to) {
        if (propertyIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("propertyIds", propertyIds)
            .addValue("fromYear", from.getYear()).addValue("fromMonth", from.getMonthValue())
            .addValue("toYear", to.getYear()).addValue("toMonth", to.getMonthValue());
        return jdbc.query("""
            SELECT r.property_id, m.room_id, r.room_number, m.meter_type, m.period_year, m.period_month,
                   m.previous_value, m.current_value, m.consumption, m.replaced
            FROM meter_readings m
            JOIN rooms r ON r.id = m.room_id
            WHERE r.property_id IN (:propertyIds)
              AND (m.period_year, m.period_month) BETWEEN (:fromYear, :fromMonth) AND (:toYear, :toMonth)
            ORDER BY r.room_number, m.meter_type, m.period_year, m.period_month
            """, params, (rs, n) -> new UtilityReading(
            UUID.fromString(rs.getString("property_id")),
            UUID.fromString(rs.getString("room_id")),
            rs.getString("room_number"),
            MeterType.valueOf(rs.getString("meter_type")),
            YearMonth.of(rs.getInt("period_year"), rs.getInt("period_month")),
            rs.getBigDecimal("previous_value"),
            rs.getBigDecimal("current_value"),
            rs.getBigDecimal("consumption"),
            rs.getBoolean("replaced")));
    }

    @Override
    public List<DuplicateRentInvoices> duplicateRentInvoices(Collection<UUID> propertyIds, YearMonth period) {
        if (propertyIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("propertyIds", propertyIds)
            .addValue("year", period.getYear()).addValue("month", period.getMonthValue());
        return jdbc.query("""
            SELECT r.property_id, i.room_id, r.room_number, array_agg(i.id::text ORDER BY i.created_at) AS invoice_ids
            FROM invoices i
            JOIN rooms r ON r.id = i.room_id
            WHERE r.property_id IN (:propertyIds)
              AND i.invoice_type = 'RENT'
              AND i.status <> 'VOIDED'
              AND i.consolidated_into_invoice_id IS NULL
              AND i.year = :year AND i.month = :month
            GROUP BY r.property_id, i.room_id, r.room_number
            HAVING COUNT(*) > 1
            """, params, (rs, n) -> new DuplicateRentInvoices(
            UUID.fromString(rs.getString("property_id")),
            UUID.fromString(rs.getString("room_id")),
            rs.getString("room_number"),
            period,
            uuids(rs.getArray("invoice_ids"))));
    }

    @Override
    public List<MissingReading> missingReadings(Collection<UUID> propertyIds, YearMonth period) {
        if (propertyIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource("propertyIds", propertyIds)
            .addValue("year", period.getYear()).addValue("month", period.getMonthValue())
            .addValue("periodStart", java.sql.Date.valueOf(period.atDay(1)))
            .addValue("periodEnd", java.sql.Date.valueOf(period.atEndOfMonth()));
        return jdbc.query("""
            SELECT DISTINCT r.property_id, r.id AS room_id, r.room_number, t.meter_type
            FROM contracts c
            JOIN rooms r ON r.id = c.room_id
            JOIN (VALUES ('ELECTRIC', 'PER_KWH'), ('WATER', 'PER_M3')) AS t(meter_type, charge_type) ON TRUE
            WHERE r.property_id IN (:propertyIds)
              AND c.status = 'ACTIVE'
              AND c.start_date <= :periodEnd AND c.end_date >= :periodStart
              AND EXISTS (SELECT 1 FROM service_rates s
                          WHERE s.property_id = r.property_id AND s.charge_type = t.charge_type)
              AND NOT EXISTS (SELECT 1 FROM meter_readings m
                              WHERE m.room_id = r.id AND m.meter_type = t.meter_type
                                AND m.period_year = :year AND m.period_month = :month)
            ORDER BY r.room_number, t.meter_type
            """, params, (rs, n) -> new MissingReading(
            UUID.fromString(rs.getString("property_id")),
            UUID.fromString(rs.getString("room_id")),
            rs.getString("room_number"),
            MeterType.valueOf(rs.getString("meter_type"))));
    }

    private static List<UUID> uuids(Array array) throws java.sql.SQLException {
        return Arrays.stream((Object[]) array.getArray()).map(o -> UUID.fromString(o.toString())).toList();
    }
}
