package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.application.port.in.InvoiceAnomalyUseCase.Anomaly;
import com.phonghub.application.port.in.InvoiceAnomalyUseCase.AnomalyReport;
import com.phonghub.application.port.in.InvoiceAnomalyUseCase.Severity;
import com.phonghub.application.port.in.InvoiceAnomalyUseCase.Type;
import com.phonghub.application.port.in.PaymentReconciliationUseCase;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationReport;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationRow;
import com.phonghub.application.port.in.PaymentReconciliationUseCase.ReconciliationStatus;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.InvoiceAnomalyQueryPort;
import com.phonghub.application.port.out.InvoiceAnomalyQueryPort.DuplicateRentInvoices;
import com.phonghub.application.port.out.InvoiceAnomalyQueryPort.MissingReading;
import com.phonghub.application.port.out.InvoiceAnomalyQueryPort.UtilityReading;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.InvoiceAnomalyService;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.PaymentReviewReason;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** #156: từng quy tắc phát hiện bất thường với dữ liệu mẫu, và phân quyền chỉ cho chủ trọ xem nhà của mình. */
class InvoiceAnomalyServiceUnitTest {

    private static final Instant NOW = Instant.parse("2026-10-14T05:00:00Z");
    private static final YearMonth PERIOD = YearMonth.of(2026, 10);

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherOwnerId = UUID.randomUUID();
    private final UUID property = UUID.randomUUID();
    private final UUID foreign = UUID.randomUUID();
    private final UUID room = UUID.randomUUID();

    private InvoiceAnomalyQueryPort port;
    private PaymentReconciliationUseCase reconciliation;
    private CurrentUserPort currentUserPort;
    private InvoiceAnomalyService service;

    @BeforeEach
    void setUp() {
        InMemoryPropertyRepository properties = new InMemoryPropertyRepository();
        properties.save(new Property(property, "Nhà A", "addr", null, 5, ownerId,
            PropertyApprovalStatus.VERIFIED, null, NOW));
        properties.save(new Property(foreign, "Nhà người khác", "addr", null, 5, otherOwnerId,
            PropertyApprovalStatus.VERIFIED, null, NOW));
        port = mock(InvoiceAnomalyQueryPort.class);
        reconciliation = mock(PaymentReconciliationUseCase.class);
        currentUserPort = mock(CurrentUserPort.class);
        AuthorizationService authorization = new AuthorizationService(new InMemoryStaffPropertyAssignmentRepository(),
            properties, new InMemoryTenantRepository(), new InMemoryContractRepository());
        service = new InvoiceAnomalyService(port, properties, currentUserPort, authorization, reconciliation,
            Clock.fixed(NOW, ZoneOffset.UTC));
        login(ownerId, UserRole.OWNER);
        when(port.utilityReadings(anyCollection(), any(), any())).thenReturn(List.of());
        when(port.missingReadings(anyCollection(), any())).thenReturn(List.of());
        when(port.duplicateRentInvoices(anyCollection(), any())).thenReturn(List.of());
        when(reconciliation.listTransactions(any())).thenReturn(report(List.of()));
    }

    private void login(UUID id, UserRole role) {
        when(currentUserPort.getCurrentUser()).thenReturn(new CurrentUser(id, role + "@x", role.name(), role));
    }

    private UtilityReading reading(MeterType type, YearMonth period, String previous, String current) {
        BigDecimal prev = new BigDecimal(previous);
        BigDecimal cur = new BigDecimal(current);
        return new UtilityReading(property, room, "101", type, period, prev, cur, cur.subtract(prev).max(BigDecimal.ZERO), false);
    }

    private void readings(UtilityReading... rows) {
        when(port.utilityReadings(anyCollection(), any(), any())).thenReturn(List.of(rows));
    }

    /** Ba kỳ trước tiêu thụ 100 mỗi kỳ (chỉ số liên tục 0 → 100 → 200 → 300). */
    private List<UtilityReading> hundredPerMonth(MeterType type) {
        return List.of(
            reading(type, YearMonth.of(2026, 7), "0", "100"),
            reading(type, YearMonth.of(2026, 8), "100", "200"),
            reading(type, YearMonth.of(2026, 9), "200", "300"));
    }

    private void withHistoryAnd(UtilityReading current) {
        UtilityReading[] all = Stream.concat(hundredPerMonth(current.type()).stream(), Stream.of(current))
            .toArray(UtilityReading[]::new);
        readings(all);
    }

    private AnomalyReport analyze() {
        return service.analyze(null, PERIOD);
    }

    private static List<Anomaly> of(AnomalyReport report, Type type) {
        return report.anomalies().stream().filter(a -> a.type() == type).toList();
    }

    private static ReconciliationReport report(List<ReconciliationRow> rows) {
        return new ReconciliationReport(rows, Map.of(), null, null, null);
    }

    @Test
    @DisplayName("Điện tăng hơn 50% so với trung bình 3 kỳ trước bị báo kèm số liệu nguồn")
    void electricitySpikeIsFlaggedWithEvidence() {
        withHistoryAnd(reading(MeterType.ELECTRIC, PERIOD, "300", "460"));

        List<Anomaly> spikes = of(analyze(), Type.UTILITY_SPIKE);

        assertEquals(1, spikes.size());
        Anomaly spike = spikes.get(0);
        assertEquals(Severity.MEDIUM, spike.severity());
        assertEquals("101", spike.roomNumber());
        assertEquals("160 kWh", spike.evidence().get("Kỳ này"));
        assertEquals("100 kWh", spike.evidence().get("Trung bình 3 kỳ trước"));
        assertEquals("60%", spike.evidence().get("Tăng"));
        assertTrue(spike.message().contains("điện tăng 60%"), spike.message());
    }

    @Test
    @DisplayName("Đúng ngưỡng 50% chưa bị báo, vượt ngưỡng mới báo")
    void spikeThresholdBoundary() {
        withHistoryAnd(reading(MeterType.ELECTRIC, PERIOD, "300", "450"));
        assertTrue(of(analyze(), Type.UTILITY_SPIKE).isEmpty());

        withHistoryAnd(reading(MeterType.ELECTRIC, PERIOD, "300", "451"));
        assertEquals(1, of(analyze(), Type.UTILITY_SPIKE).size());
    }

    @Test
    @DisplayName("Nước cũng được kiểm tra, đơn vị là m³")
    void waterSpikeUsesCubicMetres() {
        withHistoryAnd(reading(MeterType.WATER, PERIOD, "300", "500"));

        Anomaly spike = of(analyze(), Type.UTILITY_SPIKE).get(0);

        assertEquals("200 m³", spike.evidence().get("Kỳ này"));
        assertTrue(spike.message().contains("nước"), spike.message());
    }

    @Test
    @DisplayName("Chỉ dùng tối đa 3 kỳ gần nhất làm mức trung bình, kỳ cũ hơn bị bỏ")
    void baselineUsesAtMostThreePriorPeriods() {
        UtilityReading old = reading(MeterType.ELECTRIC, YearMonth.of(2026, 6), "0", "10000");
        UtilityReading j = reading(MeterType.ELECTRIC, YearMonth.of(2026, 7), "10000", "10100");
        UtilityReading a = reading(MeterType.ELECTRIC, YearMonth.of(2026, 8), "10100", "10200");
        UtilityReading s = reading(MeterType.ELECTRIC, YearMonth.of(2026, 9), "10200", "10300");
        UtilityReading cur = reading(MeterType.ELECTRIC, PERIOD, "10300", "10500");
        readings(old, j, a, s, cur);

        Anomaly spike = of(analyze(), Type.UTILITY_SPIKE).get(0);

        assertEquals("100 kWh", spike.evidence().get("Trung bình 3 kỳ trước"));
        assertEquals("100%", spike.evidence().get("Tăng"));
    }

    @Test
    @DisplayName("Chưa có lịch sử thì không kết luận tăng đột biến")
    void noHistoryProducesNoAnomaly() {
        readings(reading(MeterType.ELECTRIC, PERIOD, "0", "900"));

        assertTrue(analyze().anomalies().isEmpty());
    }

    @Test
    @DisplayName("Tiêu thụ ổn định hoặc giảm không bị báo")
    void stableOrLowerUsageIsNotFlagged() {
        withHistoryAnd(reading(MeterType.ELECTRIC, PERIOD, "300", "395"));
        assertTrue(analyze().anomalies().isEmpty());

        withHistoryAnd(reading(MeterType.ELECTRIC, PERIOD, "300", "330"));
        assertTrue(analyze().anomalies().isEmpty());
    }

    @Test
    @DisplayName("Chỉ số không đổi sau các kỳ có dùng điện bị báo mức thấp")
    void zeroConsumptionAfterUsageIsLowSeverity() {
        withHistoryAnd(reading(MeterType.ELECTRIC, PERIOD, "300", "300"));

        List<Anomaly> zero = of(analyze(), Type.METER_ZERO);

        assertEquals(1, zero.size());
        assertEquals(Severity.LOW, zero.get(0).severity());
        assertEquals("0 kWh", zero.get(0).evidence().get("Kỳ này"));
    }

    @Test
    @DisplayName("Phòng vốn không dùng điện nước (các kỳ trước đều 0) thì không báo chỉ số không đổi")
    void zeroConsumptionWithoutUsageHistoryIsIgnored() {
        readings(
            reading(MeterType.ELECTRIC, YearMonth.of(2026, 9), "50", "50"),
            reading(MeterType.ELECTRIC, PERIOD, "50", "50"));

        assertTrue(analyze().anomalies().isEmpty());
    }

    @Test
    @DisplayName("Chỉ số cuối thấp hơn chỉ số đầu bị báo mức cao")
    void currentBelowPreviousIsBackward() {
        readings(reading(MeterType.ELECTRIC, PERIOD, "500", "480"));

        List<Anomaly> backward = of(analyze(), Type.METER_BACKWARD);

        assertEquals(1, backward.size());
        assertEquals(Severity.HIGH, backward.get(0).severity());
        assertEquals("500", backward.get(0).evidence().get("Chỉ số đầu kỳ"));
        assertEquals("480", backward.get(0).evidence().get("Chỉ số cuối kỳ"));
    }

    @Test
    @DisplayName("Chỉ số cuối thấp hơn chỉ số cuối kỳ trước cũng bị báo lùi, kèm chỉ số kỳ trước")
    void currentBelowPriorPeriodIsBackward() {
        readings(
            reading(MeterType.ELECTRIC, YearMonth.of(2026, 9), "200", "300"),
            reading(MeterType.ELECTRIC, PERIOD, "100", "250"));

        Anomaly backward = of(analyze(), Type.METER_BACKWARD).get(0);

        assertEquals("300", backward.evidence().get("Chỉ số cuối kỳ trước"));
    }

    @Test
    @DisplayName("Công tơ đã thay thì không so sánh với kỳ trước")
    void replacedMeterIsSkipped() {
        UtilityReading replaced = new UtilityReading(property, room, "101", MeterType.ELECTRIC, PERIOD,
            new BigDecimal("5"), new BigDecimal("40"), new BigDecimal("35"), true);
        readings(reading(MeterType.ELECTRIC, YearMonth.of(2026, 9), "200", "9000"), replaced);

        assertTrue(analyze().anomalies().isEmpty());
    }

    @Test
    @DisplayName("Thiếu chỉ số và hóa đơn tiền thuê trùng được đưa vào báo cáo")
    void missingReadingAndDuplicateInvoice() {
        UUID invoice1 = UUID.randomUUID();
        UUID invoice2 = UUID.randomUUID();
        when(port.missingReadings(anyCollection(), eq(PERIOD)))
            .thenReturn(List.of(new MissingReading(property, room, "102", MeterType.WATER)));
        when(port.duplicateRentInvoices(anyCollection(), eq(PERIOD)))
            .thenReturn(List.of(new DuplicateRentInvoices(property, room, "103", PERIOD, List.of(invoice1, invoice2))));

        AnomalyReport result = analyze();

        Anomaly missing = of(result, Type.MISSING_READING).get(0);
        assertEquals("102", missing.roomNumber());
        assertEquals("nước", missing.evidence().get("Loại công tơ"));
        Anomaly duplicate = of(result, Type.DUPLICATE_RENT_INVOICE).get(0);
        assertEquals(Severity.HIGH, duplicate.severity());
        assertTrue(duplicate.evidence().get("Mã hóa đơn").contains(invoice1.toString()));
        assertTrue(duplicate.evidence().get("Mã hóa đơn").contains(invoice2.toString()));
    }

    @Test
    @DisplayName("Giao dịch cần xem xét quá 7 ngày bị báo, chưa đủ 7 ngày thì chưa")
    void stalePaymentReviewThreshold() {
        when(reconciliation.listTransactions(any())).thenReturn(report(List.of(
            row(Instant.parse("2026-10-06T05:00:00Z"), 111L),
            row(Instant.parse("2026-10-07T05:00:01Z"), 222L))));

        List<Anomaly> stale = of(analyze(), Type.STALE_PAYMENT_REVIEW);

        assertEquals(1, stale.size());
        assertEquals("111", stale.get(0).evidence().get("Mã giao dịch"));
        assertEquals("8", stale.get(0).evidence().get("Số ngày chờ"));
        assertEquals("OVERPAID", stale.get(0).evidence().get("Lý do"));
    }

    @Test
    @DisplayName("Báo cáo xếp mức cao trước, rồi theo phòng")
    void anomaliesAreSortedBySeverity() {
        when(port.missingReadings(anyCollection(), eq(PERIOD)))
            .thenReturn(List.of(new MissingReading(property, room, "102", MeterType.ELECTRIC)));
        readings(reading(MeterType.ELECTRIC, PERIOD, "500", "480"));

        List<Severity> order = analyze().anomalies().stream().map(Anomaly::severity).toList();

        assertEquals(List.of(Severity.HIGH, Severity.MEDIUM), order);
    }

    @Test
    @DisplayName("Chủ trọ chưa có nhà trọ nào: báo cáo rỗng, không truy vấn")
    void ownerWithoutPropertiesGetsEmptyReport() {
        login(UUID.randomUUID(), UserRole.OWNER);

        assertTrue(analyze().anomalies().isEmpty());
        verifyNoInteractions(port, reconciliation);
    }

    @Test
    @DisplayName("Chủ trọ xem nhà của người khác bị từ chối (403)")
    void ownerCannotAnalyzeAnotherOwnersProperty() {
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.analyze(foreign, PERIOD));
        verifyNoInteractions(port, reconciliation);
    }

    @Test
    @DisplayName("Quản trị viên không xem số liệu tiền (#163)")
    void adminIsDenied() {
        login(UUID.randomUUID(), UserRole.ADMIN);

        assertThrows(UnauthorizedPropertyAccessException.class, this::analyze);
        verifyNoInteractions(port, reconciliation);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"STAFF", "TECHNICIAN", "TENANT"})
    @DisplayName("Nhân viên, kỹ thuật viên và người thuê bị từ chối")
    void otherRolesAreDenied(UserRole role) {
        login(UUID.randomUUID(), role);

        assertThrows(UnauthorizedPropertyAccessException.class, this::analyze);
        verifyNoInteractions(port, reconciliation);
    }

    @Test
    @DisplayName("Chưa đăng nhập bị từ chối; thiếu kỳ phân tích là lỗi đầu vào")
    void anonymousAndMissingPeriod() {
        when(currentUserPort.getCurrentUser()).thenReturn(null);
        assertThrows(UnauthorizedPropertyAccessException.class, this::analyze);

        login(ownerId, UserRole.OWNER);
        assertThrows(IllegalArgumentException.class, () -> service.analyze(null, null));
    }

    private ReconciliationRow row(Instant receivedAt, long sepayId) {
        return new ReconciliationRow(UUID.randomUUID(), sepayId, receivedAt, "SePay", "in", "REF", null, null, null,
            ReconciliationStatus.NEEDS_REVIEW, PaymentReviewReason.OVERPAID, UUID.randomUUID(), UUID.randomUUID(),
            property, "Nhà A", "101");
    }
}
