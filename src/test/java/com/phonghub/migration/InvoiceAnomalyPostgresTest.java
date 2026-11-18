package com.phonghub.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.adapter.out.persistence.postgres.PostgresInvoiceAnomalyQueryAdapter;
import com.phonghub.application.port.in.InvoiceAnomalyUseCase.Anomaly;
import com.phonghub.application.port.in.InvoiceAnomalyUseCase.AnomalyReport;
import com.phonghub.application.port.in.InvoiceAnomalyUseCase.Type;
import com.phonghub.application.port.in.PaymentReconciliationUseCase;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationReport;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoiceAnomalyQueryPort.DuplicateRentInvoices;
import com.phonghub.application.port.out.InvoiceAnomalyQueryPort.MissingReading;
import com.phonghub.application.port.out.InvoiceAnomalyQueryPort.UtilityReading;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.InvoiceAnomalyService;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.UserRole;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * #156: các truy vấn phát hiện bất thường chạy đúng trên PostgreSQL thật, từ truy vấn đến báo cáo của service.
 * Dùng hợp đồng đang hiệu lực có sẵn trong dữ liệu mẫu docker và thêm chỉ số, đơn giá, hóa đơn cho đúng kịch bản.
 */
@Testcontainers(disabledWithoutDocker = true)
class InvoiceAnomalyPostgresTest {

    private static final String MAIN = "classpath:db/migration";
    private static final String FIXTURE = "classpath:db/seed/docker";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private NamedParameterJdbcTemplate jdbc;
    private PostgresInvoiceAnomalyQueryAdapter adapter;
    private UUID propertyId;
    private UUID roomId;
    private UUID contractId;
    private UUID tenantId;
    private String roomNumber;
    private YearMonth period;

    @BeforeEach
    void migrateAndPickContract() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(ds).locations(MAIN, FIXTURE).load().migrate();
        jdbc = new NamedParameterJdbcTemplate(ds);
        adapter = new PostgresInvoiceAnomalyQueryAdapter(jdbc);

        Map<String, Object> contract = jdbc.queryForMap("""
            SELECT c.id, c.room_id, c.property_id, c.primary_tenant_id, c.start_date, r.room_number
            FROM contracts c JOIN rooms r ON r.id = c.room_id
            WHERE c.status = 'ACTIVE' ORDER BY c.created_at LIMIT 1
            """, new MapSqlParameterSource());
        contractId = (UUID) contract.get("id");
        roomId = (UUID) contract.get("room_id");
        propertyId = (UUID) contract.get("property_id");
        tenantId = (UUID) contract.get("primary_tenant_id");
        roomNumber = (String) contract.get("room_number");
        period = YearMonth.from(((Date) contract.get("start_date")).toLocalDate()).plusMonths(0);

        MapSqlParameterSource room = new MapSqlParameterSource("room", roomId);
        jdbc.update("DELETE FROM meter_readings WHERE room_id = :room", room);
        jdbc.update("DELETE FROM invoice_items WHERE invoice_id IN (SELECT id FROM invoices WHERE room_id = :room)", room);
        jdbc.update("DELETE FROM invoices WHERE room_id = :room", room);
    }

    private void reading(MeterType type, YearMonth month, String previous, String current) {
        jdbc.update("""
            INSERT INTO meter_readings (room_id, contract_id, meter_type, previous_value, current_value, consumption,
                                        reading_date, period_year, period_month)
            VALUES (:room, :contract, :type, :prev, :cur, :cur - :prev, :date, :y, :m)
            """, new MapSqlParameterSource()
            .addValue("room", roomId).addValue("contract", contractId).addValue("type", type.name())
            .addValue("prev", new java.math.BigDecimal(previous)).addValue("cur", new java.math.BigDecimal(current))
            .addValue("date", Date.valueOf(month.atEndOfMonth()))
            .addValue("y", month.getYear()).addValue("m", month.getMonthValue()));
    }

    private void rate(String serviceName, String chargeType) {
        jdbc.update("""
            INSERT INTO service_rates (property_id, service_name, charge_type, price_per_unit)
            SELECT :property, :name, :charge, 3000
            WHERE NOT EXISTS (SELECT 1 FROM service_rates WHERE property_id = :property AND charge_type = :charge)
            """, new MapSqlParameterSource().addValue("property", propertyId)
            .addValue("name", serviceName).addValue("charge", chargeType));
    }

    private UUID invoice(UUID contract) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO invoices (id, contract_id, room_id, tenant_id, month, year, total_amount, due_date, status)
            VALUES (:id, :contract, :room, :tenant, :m, :y, 3000000, :due, 'ISSUED')
            """, new MapSqlParameterSource().addValue("id", id).addValue("contract", contract)
            .addValue("room", roomId).addValue("tenant", tenantId)
            .addValue("m", period.getMonthValue()).addValue("y", period.getYear())
            .addValue("due", Date.valueOf(LocalDate.of(period.getYear(), period.getMonthValue(), 5))));
        return id;
    }

    /** Hợp đồng thứ hai của cùng phòng (đã hết hạn nên không vi phạm ràng buộc một hợp đồng hiệu lực mỗi phòng). */
    private UUID secondContract() {
        UUID id = UUID.randomUUID();
        // Một câu lệnh duy nhất: bảng tạm chỉ tồn tại trong một kết nối, mà JdbcTemplate có thể mở kết nối mới mỗi lần.
        jdbc.getJdbcOperations().execute("""
            DO $$
            BEGIN
                CREATE TEMP TABLE c2 AS SELECT * FROM contracts WHERE id = '%s';
                UPDATE c2 SET id = '%s', status = 'EXPIRED';
                INSERT INTO contracts SELECT * FROM c2;
                DROP TABLE c2;
            END $$
            """.formatted(contractId, id));
        return id;
    }

    @Test
    @DisplayName("chỉ số điện/nước theo kỳ được trả đủ trong cửa sổ và chỉ thuộc phạm vi nhà trọ")
    void utilityReadingsAreScopedAndWindowed() {
        reading(MeterType.ELECTRIC, period.minusMonths(4), "0", "9999");
        reading(MeterType.ELECTRIC, period.minusMonths(3), "9999", "10099");
        reading(MeterType.ELECTRIC, period.minusMonths(1), "10099", "10199");
        reading(MeterType.WATER, period, "0", "12");

        List<UtilityReading> rows = adapter.utilityReadings(List.of(propertyId), period.minusMonths(3), period);

        assertEquals(3, rows.size(), "kỳ -4 nằm ngoài cửa sổ");
        UtilityReading water = rows.stream().filter(r -> r.type() == MeterType.WATER).findFirst().orElseThrow();
        assertEquals(roomNumber, water.roomNumber());
        assertEquals(0, water.consumption().compareTo(new java.math.BigDecimal("12")));
        assertEquals(propertyId, water.propertyId());
        assertTrue(adapter.utilityReadings(List.of(UUID.randomUUID()), period.minusMonths(3), period).isEmpty());
        assertTrue(adapter.utilityReadings(List.of(), period.minusMonths(3), period).isEmpty());
    }

    @Test
    @DisplayName("thiếu chỉ số: chỉ báo loại công tơ có đơn giá theo đơn vị và chưa có chỉ số kỳ đó")
    void missingReadingsFollowServiceRates() {
        jdbc.update("DELETE FROM service_rates WHERE property_id = :p", new MapSqlParameterSource("p", propertyId));
        assertTrue(adapter.missingReadings(List.of(propertyId), period).isEmpty(), "chưa có đơn giá thì chưa cần chỉ số");

        rate("Điện", "PER_KWH");
        rate("Nước", "PER_M3");
        List<MissingReading> both = adapter.missingReadings(List.of(propertyId), period);
        assertEquals(List.of(MeterType.ELECTRIC, MeterType.WATER), both.stream().map(MissingReading::type).toList());
        assertEquals(roomNumber, both.get(0).roomNumber());

        reading(MeterType.ELECTRIC, period, "0", "100");
        List<MissingReading> waterOnly = adapter.missingReadings(List.of(propertyId), period);
        assertEquals(List.of(MeterType.WATER), waterOnly.stream().map(MissingReading::type).toList());

        assertTrue(adapter.missingReadings(List.of(propertyId), period.minusYears(5)).isEmpty(),
            "ngoài thời hạn hợp đồng thì không báo thiếu");
        assertTrue(adapter.missingReadings(List.of(UUID.randomUUID()), period).isEmpty());
    }

    @Test
    @DisplayName("hóa đơn tiền thuê trùng kỳ: cùng hợp đồng bị DB chặn, hai hợp đồng cùng phòng được phát hiện")
    void duplicateRentInvoicesAcrossContracts() {
        UUID first = invoice(contractId);
        assertThrows(DataIntegrityViolationException.class, () -> invoice(contractId),
            "chỉ mục uk_invoices_rent_period chặn trùng trong cùng hợp đồng");
        assertTrue(adapter.duplicateRentInvoices(List.of(propertyId), period).isEmpty());

        UUID second = invoice(secondContract());
        List<DuplicateRentInvoices> duplicates = adapter.duplicateRentInvoices(List.of(propertyId), period);

        assertEquals(1, duplicates.size());
        assertEquals(roomNumber, duplicates.get(0).roomNumber());
        assertEquals(2, duplicates.get(0).invoiceIds().size());
        assertTrue(duplicates.get(0).invoiceIds().containsAll(List.of(first, second)));

        jdbc.update("UPDATE invoices SET status = 'VOIDED' WHERE id = :id", new MapSqlParameterSource("id", second));
        assertTrue(adapter.duplicateRentInvoices(List.of(propertyId), period).isEmpty(), "hóa đơn đã hủy không tính");
        assertTrue(adapter.duplicateRentInvoices(List.of(UUID.randomUUID()), period).isEmpty());
    }

    @Test
    @DisplayName("báo cáo đầy đủ từ PostgreSQL: điện tăng vọt, thiếu chỉ số nước và hóa đơn trùng")
    void endToEndReport() {
        rate("Điện", "PER_KWH");
        rate("Nước", "PER_M3");
        reading(MeterType.ELECTRIC, period.minusMonths(3), "0", "100");
        reading(MeterType.ELECTRIC, period.minusMonths(2), "100", "200");
        reading(MeterType.ELECTRIC, period.minusMonths(1), "200", "300");
        reading(MeterType.ELECTRIC, period, "300", "500");
        invoice(contractId);
        invoice(secondContract());

        UUID ownerId = UUID.randomUUID();
        InMemoryPropertyRepository properties = new InMemoryPropertyRepository();
        properties.save(new Property(propertyId, "Nhà A", "addr", null, 5, ownerId,
            PropertyApprovalStatus.VERIFIED, null, Instant.now()));
        CurrentUserPort currentUser = mock(CurrentUserPort.class);
        when(currentUser.getCurrentUser()).thenReturn(new CurrentUser(ownerId, "o@x", "Owner", UserRole.OWNER));
        PaymentReconciliationUseCase reconciliation = mock(PaymentReconciliationUseCase.class);
        when(reconciliation.listTransactions(any()))
            .thenReturn(new ReconciliationReport(List.of(), Map.of(), null, null, null));
        InvoiceAnomalyService service = new InvoiceAnomalyService(adapter, properties, currentUser,
            new AuthorizationService(new InMemoryStaffPropertyAssignmentRepository(), properties,
                new InMemoryTenantRepository(), new InMemoryContractRepository()),
            reconciliation, Clock.fixed(Instant.now(), ZoneOffset.UTC));

        AnomalyReport report = service.analyze(null, period);

        List<Type> types = report.anomalies().stream().map(Anomaly::type).toList();
        assertTrue(types.contains(Type.UTILITY_SPIKE), types.toString());
        assertTrue(types.contains(Type.MISSING_READING), types.toString());
        assertTrue(types.contains(Type.DUPLICATE_RENT_INVOICE), types.toString());
        Anomaly spike = report.anomalies().stream().filter(a -> a.type() == Type.UTILITY_SPIKE).findFirst().orElseThrow();
        assertEquals("200 kWh", spike.evidence().get("Kỳ này"));
        assertEquals("100 kWh", spike.evidence().get("Trung bình 3 kỳ trước"));
    }
}
