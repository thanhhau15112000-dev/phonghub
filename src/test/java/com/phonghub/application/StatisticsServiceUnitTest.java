package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.phonghub.adapter.out.persistence.inmemory.InMemoryContractRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryPropertyRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryStaffPropertyAssignmentRepository;
import com.phonghub.adapter.out.persistence.inmemory.InMemoryTenantRepository;
import com.phonghub.application.port.in.StatisticsUseCase.AccountStats;
import com.phonghub.application.port.in.StatisticsUseCase.AdminOverview;
import com.phonghub.application.port.in.StatisticsUseCase.Granularity;
import com.phonghub.application.port.in.StatisticsUseCase.OwnerSummary;
import com.phonghub.application.port.in.StatisticsUseCase.PeriodStat;
import com.phonghub.application.port.in.StatisticsUseCase.PropertyStat;
import com.phonghub.application.port.in.StatisticsUseCase.StatsQuery;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.StatisticsQueryPort;
import com.phonghub.application.port.out.StatisticsQueryPort.ActivityCount;
import com.phonghub.application.port.out.StatisticsQueryPort.ApprovalCount;
import com.phonghub.application.port.out.StatisticsQueryPort.InvoiceAggregate;
import com.phonghub.application.port.out.StatisticsQueryPort.MaintenanceAggregate;
import com.phonghub.application.port.out.StatisticsQueryPort.OpenMaintenanceCount;
import com.phonghub.application.port.out.StatisticsQueryPort.PendingPropertyRow;
import com.phonghub.application.port.out.StatisticsQueryPort.RoomStatusCount;
import com.phonghub.application.port.out.StatisticsQueryPort.SignupCount;
import com.phonghub.application.port.out.StatisticsQueryPort.UserCount;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.StatisticsService;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

/** #93: số liệu thống kê, nhóm kỳ và phân quyền ở tầng service. */
class StatisticsServiceUnitTest {

    private static final Instant NOW = Instant.parse("2026-10-14T05:00:00Z");
    private static final YearMonth FROM = YearMonth.of(2026, 1);
    private static final YearMonth TO = YearMonth.of(2026, 6);

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherOwnerId = UUID.randomUUID();
    private final UUID p1 = UUID.randomUUID();
    private final UUID p2 = UUID.randomUUID();
    private final UUID foreign = UUID.randomUUID();

    private StatisticsQueryPort port;
    private CurrentUserPort currentUserPort;
    private StatisticsService service;

    @BeforeEach
    void setUp() {
        InMemoryPropertyRepository properties = new InMemoryPropertyRepository();
        properties.save(property(p1, "B Nhà trọ", ownerId));
        properties.save(property(p2, "A Nhà trọ", ownerId));
        properties.save(property(foreign, "Nhà người khác", otherOwnerId));
        port = mock(StatisticsQueryPort.class);
        currentUserPort = mock(CurrentUserPort.class);
        AuthorizationService authorization = new AuthorizationService(new InMemoryStaffPropertyAssignmentRepository(),
            properties, new InMemoryTenantRepository(), new InMemoryContractRepository());
        service = new StatisticsService(port, properties, currentUserPort, authorization,
            Clock.fixed(NOW, ZoneOffset.UTC));
        login(ownerId, UserRole.OWNER);
    }

    private static Property property(UUID id, String name, UUID owner) {
        return new Property(id, name, "addr", null, 5, owner, PropertyApprovalStatus.VERIFIED, null, NOW);
    }

    private void login(UUID id, UserRole role) {
        when(currentUserPort.getCurrentUser()).thenReturn(new CurrentUser(id, role + "@x", role.name(), role));
    }

    private static InvoiceAggregate invoices(UUID property, YearMonth period, String billed, String collected,
                                             String outstanding, long overdue, String overdueAmount) {
        return new InvoiceAggregate(property, period, 2, new BigDecimal(billed), new BigDecimal(collected),
            new BigDecimal(outstanding), overdue, new BigDecimal(overdueAmount));
    }

    private StatsQuery query(UUID propertyId, Granularity groupBy) {
        return new StatsQuery(propertyId, FROM, TO, groupBy);
    }

    @Test
    @DisplayName("tổng hợp doanh thu, công nợ, tỷ lệ thu, chi phí, lợi nhuận, lấp đầy và bảo trì")
    void ownerSummaryTotals() {
        when(port.invoiceAggregates(anyCollection(), eq(FROM), eq(TO), eq(LocalDate.of(2026, 10, 14)))).thenReturn(List.of(
            invoices(p1, YearMonth.of(2026, 1), "3000000", "3000000", "0", 0, "0"),
            invoices(p1, YearMonth.of(2026, 2), "3000000", "1000000", "2000000", 1, "2000000"),
            invoices(p2, YearMonth.of(2026, 2), "2000000", "0", "2000000", 0, "0")));
        when(port.completedMaintenance(anyCollection(), any(), any())).thenReturn(List.of(
            new MaintenanceAggregate(p1, YearMonth.of(2026, 2), 2, new BigDecimal("500000"), 30.0),
            new MaintenanceAggregate(p2, YearMonth.of(2026, 3), 1, new BigDecimal("100000"), 6.0)));
        when(port.roomStatusCounts(anyCollection())).thenReturn(List.of(
            new RoomStatusCount(p1, RoomStatus.OCCUPIED, 3), new RoomStatusCount(p1, RoomStatus.MAINTENANCE, 1),
            new RoomStatusCount(p2, RoomStatus.AVAILABLE, 2), new RoomStatusCount(p2, RoomStatus.OCCUPIED, 1),
            new RoomStatusCount(p2, RoomStatus.RESERVED, 1)));
        when(port.openMaintenance(anyCollection(), eq(NOW))).thenReturn(List.of(new OpenMaintenanceCount(p1, 3, 1)));

        OwnerSummary summary = service.ownerSummary(query(null, null));

        assertEquals(2, summary.propertyCount());
        assertEquals(6, summary.revenue().invoiceCount());
        assertEquals(new BigDecimal("8000000"), summary.revenue().billed());
        assertEquals(new BigDecimal("4000000"), summary.revenue().collected());
        assertEquals(new BigDecimal("4000000"), summary.revenue().outstanding());
        assertEquals(1, summary.revenue().overdueInvoices());
        assertEquals(new BigDecimal("2000000"), summary.revenue().overdueAmount());
        assertEquals(new BigDecimal("50.0"), summary.revenue().collectionRate());
        assertEquals(new BigDecimal("600000"), summary.repairCost());
        assertEquals(new BigDecimal("3400000"), summary.estimatedProfit());
        assertEquals(8, summary.occupancy().totalRooms());
        assertEquals(4, summary.occupancy().occupied());
        assertEquals(1, summary.occupancy().reserved());
        assertEquals(1, summary.occupancy().maintenance());
        assertEquals(2, summary.occupancy().available());
        assertEquals(new BigDecimal("50.0"), summary.occupancy().occupancyRate());
        assertEquals(3, summary.maintenance().openTickets());
        assertEquals(1, summary.maintenance().overdueTickets());
        assertEquals(1, summary.maintenance().roomsUnderMaintenance());
        assertEquals(3, summary.maintenance().completedTickets());
        assertEquals(new BigDecimal("12.0"), summary.maintenance().avgResolutionHours());
        assertEquals("2026-01", summary.from());
        assertEquals("2026-06", summary.to());
    }

    @Test
    @DisplayName("owner chỉ truy vấn trên nhà của mình; khoảng hoàn thành bảo trì theo giờ Việt Nam")
    void ownerScopeAndMaintenanceWindow() {
        service.ownerSummary(query(null, null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(port).invoiceAggregates(ids.capture(), eq(FROM), eq(TO), any());
        assertEquals(Set.of(p1, p2), Set.copyOf(ids.getValue()));
        verify(port).completedMaintenance(anyCollection(), eq(Instant.parse("2025-12-31T17:00:00Z")),
            eq(Instant.parse("2026-06-30T17:00:00Z")));
    }

    @Test
    @DisplayName("không có dữ liệu thì trả 0 và tỷ lệ null, không lỗi chia cho 0")
    void emptyData() {
        login(UUID.randomUUID(), UserRole.OWNER);

        OwnerSummary summary = service.ownerSummary(query(null, null));

        assertEquals(0, summary.propertyCount());
        assertEquals(BigDecimal.ZERO, summary.revenue().billed());
        assertNull(summary.revenue().collectionRate());
        assertNull(summary.occupancy().occupancyRate());
        assertNull(summary.maintenance().avgResolutionHours());
        assertEquals(BigDecimal.ZERO, summary.estimatedProfit());
        assertEquals(6, service.ownerRevenue(query(null, Granularity.MONTH)).size(), "đủ 6 tháng dù không có số liệu");
        assertTrue(service.ownerProperties(query(null, null)).isEmpty());
    }

    @Test
    @DisplayName("chuỗi doanh thu theo tháng/quý/năm, đủ kỳ và lợi nhuận = đã thu − chi phí")
    void revenueSeries() {
        when(port.invoiceAggregates(anyCollection(), any(), any(), any())).thenReturn(List.of(
            invoices(p1, YearMonth.of(2026, 1), "100", "100", "0", 0, "0"),
            invoices(p2, YearMonth.of(2026, 3), "50", "20", "30", 0, "0"),
            invoices(p1, YearMonth.of(2026, 4), "70", "70", "0", 0, "0")));
        when(port.completedMaintenance(anyCollection(), any(), any())).thenReturn(List.of(
            new MaintenanceAggregate(p1, YearMonth.of(2026, 3), 1, new BigDecimal("15"), 1.0)));

        List<PeriodStat> months = service.ownerRevenue(query(null, Granularity.MONTH));
        assertEquals(List.of("2026-01", "2026-02", "2026-03", "2026-04", "2026-05", "2026-06"),
            months.stream().map(PeriodStat::period).toList());
        assertEquals(BigDecimal.ZERO, months.get(1).billed());
        assertEquals(new BigDecimal("5"), months.get(2).estimatedProfit());

        List<PeriodStat> quarters = service.ownerRevenue(query(null, Granularity.QUARTER));
        assertEquals(List.of("2026-Q1", "2026-Q2"), quarters.stream().map(PeriodStat::period).toList());
        assertEquals(new BigDecimal("150"), quarters.get(0).billed());
        assertEquals(new BigDecimal("120"), quarters.get(0).collected());
        assertEquals(new BigDecimal("30"), quarters.get(0).outstanding());
        assertEquals(new BigDecimal("15"), quarters.get(0).repairCost());
        assertEquals(new BigDecimal("105"), quarters.get(0).estimatedProfit());

        List<PeriodStat> years = service.ownerRevenue(new StatsQuery(null, YearMonth.of(2025, 11), TO, Granularity.YEAR));
        assertEquals(List.of("2025", "2026"), years.stream().map(PeriodStat::period).toList());
        assertEquals(new BigDecimal("220"), years.get(1).billed());
    }

    @Test
    @DisplayName("số liệu từng nhà trọ, sắp theo tên")
    void perProperty() {
        when(port.invoiceAggregates(anyCollection(), any(), any(), any())).thenReturn(List.of(
            invoices(p1, YearMonth.of(2026, 2), "300", "100", "200", 1, "200")));
        when(port.roomStatusCounts(anyCollection())).thenReturn(List.of(new RoomStatusCount(p2, RoomStatus.OCCUPIED, 2)));

        List<PropertyStat> stats = service.ownerProperties(query(null, null));

        assertEquals(List.of("A Nhà trọ", "B Nhà trọ"), stats.stream().map(PropertyStat::name).toList());
        assertEquals(new BigDecimal("100.0"), stats.get(0).occupancy().occupancyRate());
        assertEquals(BigDecimal.ZERO, stats.get(0).revenue().billed());
        assertEquals(new BigDecimal("300"), stats.get(1).revenue().billed());
        assertEquals(1, stats.get(1).revenue().overdueInvoices());
    }

    @Test
    @DisplayName("owner lọc nhà người khác bị 403 trước khi truy vấn")
    void ownerForbiddenOnForeignProperty() {
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.ownerSummary(query(foreign, null)));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.ownerRevenue(query(foreign, null)));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.ownerProperties(query(foreign, null)));
        verifyNoInteractions(port);
    }

    @Test
    @DisplayName("owner lọc một nhà của mình thì chỉ truy vấn nhà đó")
    void ownerSingleProperty() {
        OwnerSummary summary = service.ownerSummary(query(p1, null));

        assertEquals(1, summary.propertyCount());
        verify(port).invoiceAggregates(eq(List.of(p1)), any(), any(), any());
    }

    @Test
    @DisplayName("admin xem toàn bộ hoặc một nhà bất kỳ; nhà không tồn tại trả lỗi không tìm thấy")
    void adminScope() {
        login(UUID.randomUUID(), UserRole.ADMIN);

        assertEquals(3, service.ownerSummary(query(null, null)).propertyCount());
        assertEquals(1, service.ownerSummary(query(foreign, null)).propertyCount());
        assertThrows(PropertyNotFoundException.class, () -> service.ownerSummary(query(UUID.randomUUID(), null)));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"STAFF", "TECHNICIAN", "TENANT"})
    @DisplayName("staff, kỹ thuật viên, người thuê không xem thống kê tài chính và thống kê admin")
    void otherRolesForbidden(UserRole role) {
        login(UUID.randomUUID(), role);

        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.ownerSummary(query(null, null)));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.ownerRevenue(query(p1, null)));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.ownerProperties(query(null, null)));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.adminOverview(query(null, null)));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.pendingProperties());
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.adminAccounts(query(null, null)));
        verifyNoInteractions(port);
    }

    @Test
    @DisplayName("owner không xem được thống kê admin; chưa đăng nhập bị từ chối")
    void adminOnlyAndUnauthenticated() {
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.adminOverview(query(null, null)));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.pendingProperties());
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.adminAccounts(query(null, null)));

        when(currentUserPort.getCurrentUser()).thenReturn(null);
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.ownerSummary(query(null, null)));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.adminOverview(query(null, null)));
        verifyNoInteractions(port);
    }

    @Test
    @DisplayName("admin: tổng quan toàn hệ thống dùng phạm vi null (không giới hạn nhà trọ)")
    void adminOverview() {
        login(UUID.randomUUID(), UserRole.ADMIN);
        when(port.propertyApprovalCounts()).thenReturn(List.of(
            new ApprovalCount(PropertyApprovalStatus.VERIFIED, 4), new ApprovalCount(PropertyApprovalStatus.PENDING, 2)));
        when(port.userCounts()).thenReturn(List.of(
            new UserCount(UserRole.OWNER, User.UserStatus.ACTIVE, 3), new UserCount(UserRole.TENANT, User.UserStatus.ACTIVE, 7),
            new UserCount(UserRole.TENANT, User.UserStatus.INACTIVE, 1)));
        when(port.invoiceAggregates(isNull(), any(), any(), any())).thenReturn(List.of(
            invoices(p1, YearMonth.of(2026, 2), "1000", "400", "600", 0, "0")));
        when(port.roomStatusCounts(isNull())).thenReturn(List.of(new RoomStatusCount(p1, RoomStatus.OCCUPIED, 1)));

        AdminOverview overview = service.adminOverview(query(null, null));

        assertEquals(6, overview.properties());
        assertEquals(2, overview.propertiesWithStatus("PENDING"));
        assertEquals(0, overview.propertiesWithStatus("REJECTED"));
        assertEquals(3, overview.accounts("OWNER"));
        assertEquals(8, overview.accounts("TENANT"));
        assertEquals(0, overview.accounts("STAFF"));
        assertEquals(new BigDecimal("400"), overview.revenue().collected());
        assertEquals(new BigDecimal("40.0"), overview.revenue().collectionRate());
        assertEquals(new BigDecimal("100.0"), overview.occupancy().occupancyRate());
        verify(port, never()).invoiceAggregates(anyCollection(), any(), any(), any());
    }

    @Test
    @DisplayName("admin: nhà chờ duyệt và tài khoản/hoạt động theo tháng (đủ tháng, tháng trống = 0)")
    void adminPendingAndAccounts() {
        login(UUID.randomUUID(), UserRole.ADMIN);
        UUID pendingOwner = UUID.randomUUID();
        when(port.pendingProperties()).thenReturn(List.of(new PendingPropertyRow(p1, "Chờ duyệt", "addr", pendingOwner,
            "Chủ nhà", "owner@x", NOW)));
        when(port.userCounts()).thenReturn(List.of(
            new UserCount(UserRole.OWNER, User.UserStatus.ACTIVE, 2), new UserCount(UserRole.STAFF, User.UserStatus.SUSPENDED, 1)));
        when(port.userSignups(any(), any())).thenReturn(List.of(
            new SignupCount(YearMonth.of(2026, 2), UserRole.OWNER, 2), new SignupCount(YearMonth.of(2026, 2), UserRole.TENANT, 1)));
        when(port.auditActivity(any(), any())).thenReturn(List.of(new ActivityCount(YearMonth.of(2026, 3), 9, 4)));

        assertEquals("Chủ nhà", service.pendingProperties().get(0).ownerName());

        AccountStats accounts = service.adminAccounts(query(null, null));
        assertEquals(3, accounts.totalAccounts());
        assertEquals(2, accounts.byRole().get(UserRole.OWNER));
        assertEquals(1, accounts.byStatus().get(User.UserStatus.SUSPENDED));
        assertEquals(0, accounts.byStatus().get(User.UserStatus.INACTIVE));
        assertEquals(6, accounts.activity().size());
        assertEquals(3, accounts.activity().get(1).newAccounts());
        assertEquals(1, accounts.activity().get(1).newAccountsByRole().get(UserRole.TENANT));
        assertEquals(9, accounts.activity().get(2).actions());
        assertEquals(4, accounts.activity().get(2).activeUsers());
        assertEquals(0, accounts.activity().get(5).actions());
        verify(port).userSignups(Instant.parse("2025-12-31T17:00:00Z"), Instant.parse("2026-06-30T17:00:00Z"));
    }

    @Test
    @DisplayName("khoảng thời gian: mặc định 12 tháng, chặn from > to và quá 60 tháng")
    void queryValidation() {
        YearMonth current = YearMonth.of(2026, 10);
        StatsQuery defaults = StatsQuery.of(null, null, null, null, current);
        assertEquals(YearMonth.of(2025, 11), defaults.from());
        assertEquals(current, defaults.to());
        assertEquals(Granularity.MONTH, defaults.groupBy());
        assertEquals(YearMonth.of(2026, 3), StatsQuery.of(null, null, YearMonth.of(2026, 3), null, current).to());
        assertEquals(YearMonth.of(2025, 4), StatsQuery.of(null, null, YearMonth.of(2026, 3), null, current).from());

        assertThrows(IllegalArgumentException.class, () -> new StatsQuery(null, TO, FROM, null));
        assertThrows(IllegalArgumentException.class,
            () -> new StatsQuery(null, YearMonth.of(2020, 1), YearMonth.of(2025, 1), null));
        assertEquals(60, java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.of(2021, 1), YearMonth.of(2025, 12)) + 1);
        new StatsQuery(null, YearMonth.of(2021, 1), YearMonth.of(2025, 12), null);
        assertThrows(IllegalArgumentException.class, () -> new StatsQuery(null, null, TO, null));
    }
}
