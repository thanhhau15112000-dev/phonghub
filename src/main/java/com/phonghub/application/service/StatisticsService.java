package com.phonghub.application.service;

import com.phonghub.application.port.in.StatisticsUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.StatisticsQueryPort;
import com.phonghub.application.port.out.StatisticsQueryPort.InvoiceAggregate;
import com.phonghub.application.port.out.StatisticsQueryPort.MaintenanceAggregate;
import com.phonghub.application.port.out.StatisticsQueryPort.OpenMaintenanceCount;
import com.phonghub.application.port.out.StatisticsQueryPort.RoomStatusCount;
import com.phonghub.domain.exception.PropertyNotFoundException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StatisticsService implements StatisticsUseCase {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final StatisticsQueryPort statsQuery;
    private final PropertyRepositoryPort propertyRepository;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    public StatisticsService(
        StatisticsQueryPort statsQuery,
        PropertyRepositoryPort propertyRepository,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        Clock clock
    ) {
        this.statsQuery = statsQuery;
        this.propertyRepository = propertyRepository;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.clock = clock;
    }

    @Override
    public OwnerSummary ownerSummary(StatsQuery query) {
        List<Property> scope = ownerScope(query.propertyId());
        Collection<UUID> ids = ids(scope);

        List<InvoiceAggregate> invoices = statsQuery.invoiceAggregates(ids, query.from(), query.to(), today());
        List<MaintenanceAggregate> completed = completedMaintenance(ids, query);
        RevenueTotals revenue = revenue(invoices);
        BigDecimal repairCost = sum(completed, MaintenanceAggregate::ownerRepairCost);
        List<RoomStatusCount> rooms = statsQuery.roomStatusCounts(ids);

        return new OwnerSummary(query.from().toString(), query.to().toString(), query.propertyId(), scope.size(),
            revenue, repairCost, revenue.collected().subtract(repairCost), occupancy(rooms),
            maintenance(statsQuery.openMaintenance(ids, clock.instant()), rooms, completed));
    }

    @Override
    public List<PeriodStat> ownerRevenue(StatsQuery query) {
        Collection<UUID> ids = ids(ownerScope(query.propertyId()));
        List<InvoiceAggregate> invoices = statsQuery.invoiceAggregates(ids, query.from(), query.to(), today());
        List<MaintenanceAggregate> completed = completedMaintenance(ids, query);

        Map<String, List<InvoiceAggregate>> invoicesByPeriod = invoices.stream()
            .collect(Collectors.groupingBy(a -> periodKey(a.period(), query.groupBy())));
        Map<String, List<MaintenanceAggregate>> costsByPeriod = completed.stream()
            .collect(Collectors.groupingBy(a -> periodKey(a.period(), query.groupBy())));

        List<PeriodStat> result = new ArrayList<>();
        for (String period : periodKeys(query)) {
            List<InvoiceAggregate> rows = invoicesByPeriod.getOrDefault(period, List.of());
            BigDecimal collected = sum(rows, InvoiceAggregate::collected);
            BigDecimal repairCost = sum(costsByPeriod.getOrDefault(period, List.of()),
                MaintenanceAggregate::ownerRepairCost);
            result.add(new PeriodStat(period, sum(rows, InvoiceAggregate::billed), collected,
                sum(rows, InvoiceAggregate::outstanding), repairCost, collected.subtract(repairCost)));
        }
        return result;
    }

    @Override
    public List<PropertyStat> ownerProperties(StatsQuery query) {
        List<Property> scope = ownerScope(query.propertyId());
        Collection<UUID> ids = ids(scope);

        Map<UUID, List<InvoiceAggregate>> invoices = statsQuery.invoiceAggregates(ids, query.from(), query.to(), today())
            .stream().collect(Collectors.groupingBy(InvoiceAggregate::propertyId));
        Map<UUID, List<MaintenanceAggregate>> costs = completedMaintenance(ids, query).stream()
            .collect(Collectors.groupingBy(MaintenanceAggregate::propertyId));
        Map<UUID, List<RoomStatusCount>> rooms = statsQuery.roomStatusCounts(ids).stream()
            .collect(Collectors.groupingBy(RoomStatusCount::propertyId));

        return scope.stream()
            .sorted(Comparator.comparing(Property::name, String.CASE_INSENSITIVE_ORDER))
            .map(p -> {
                RevenueTotals revenue = revenue(invoices.getOrDefault(p.id(), List.of()));
                BigDecimal repairCost = sum(costs.getOrDefault(p.id(), List.of()), MaintenanceAggregate::ownerRepairCost);
                return new PropertyStat(p.id(), p.name(), p.approvalStatus(), revenue, repairCost,
                    revenue.collected().subtract(repairCost), occupancy(rooms.getOrDefault(p.id(), List.of())));
            })
            .toList();
    }

    @Override
    public AdminOverview adminOverview(StatsQuery query) {
        authorizationService.assertAdmin(currentUserPort.getCurrentUser());

        Map<PropertyApprovalStatus, Long> byApproval = new EnumMap<>(PropertyApprovalStatus.class);
        for (PropertyApprovalStatus status : PropertyApprovalStatus.values()) {
            byApproval.put(status, 0L);
        }
        statsQuery.propertyApprovalCounts().forEach(c -> byApproval.merge(c.status(), c.count(), Long::sum));

        Map<UserRole, Long> byRole = zeroed(UserRole.class);
        statsQuery.userCounts().forEach(c -> byRole.merge(c.role(), c.count(), Long::sum));

        return new AdminOverview(query.from().toString(), query.to().toString(),
            byApproval.values().stream().mapToLong(Long::longValue).sum(), byApproval, byRole,
            revenue(statsQuery.invoiceAggregates(null, query.from(), query.to(), today())),
            occupancy(statsQuery.roomStatusCounts(null)));
    }

    @Override
    public List<PendingProperty> pendingProperties() {
        authorizationService.assertAdmin(currentUserPort.getCurrentUser());
        return statsQuery.pendingProperties().stream()
            .map(p -> new PendingProperty(p.id(), p.name(), p.address(), p.ownerId(), p.ownerName(), p.ownerEmail(),
                p.createdAt()))
            .toList();
    }

    @Override
    public AccountStats adminAccounts(StatsQuery query) {
        authorizationService.assertAdmin(currentUserPort.getCurrentUser());

        Map<UserRole, Long> byRole = zeroed(UserRole.class);
        Map<User.UserStatus, Long> byStatus = zeroed(User.UserStatus.class);
        statsQuery.userCounts().forEach(c -> {
            byRole.merge(c.role(), c.count(), Long::sum);
            byStatus.merge(c.status(), c.count(), Long::sum);
        });

        Instant from = startOf(query.from());
        Instant to = startOf(query.to().plusMonths(1));
        Map<YearMonth, List<StatisticsQueryPort.SignupCount>> signups = statsQuery.userSignups(from, to).stream()
            .collect(Collectors.groupingBy(StatisticsQueryPort.SignupCount::period));
        Map<YearMonth, StatisticsQueryPort.ActivityCount> activity = statsQuery.auditActivity(from, to).stream()
            .collect(Collectors.toMap(StatisticsQueryPort.ActivityCount::period, Function.identity(), (a, b) -> a));

        List<ActivityPeriod> periods = new ArrayList<>();
        for (YearMonth month = query.from(); !month.isAfter(query.to()); month = month.plusMonths(1)) {
            Map<UserRole, Long> newByRole = zeroed(UserRole.class);
            signups.getOrDefault(month, List.of()).forEach(s -> newByRole.merge(s.role(), s.count(), Long::sum));
            StatisticsQueryPort.ActivityCount act = activity.get(month);
            periods.add(new ActivityPeriod(month.toString(), newByRole.values().stream().mapToLong(Long::longValue).sum(),
                newByRole, act == null ? 0 : act.actions(), act == null ? 0 : act.activeUsers()));
        }

        return new AccountStats(query.from().toString(), query.to().toString(),
            byRole.values().stream().mapToLong(Long::longValue).sum(), byRole, byStatus, periods);
    }

    /**
     * Phạm vi nhà trọ được xem số liệu tài chính: owner chỉ nhà của mình, admin mọi nhà. Staff, kỹ thuật viên và
     * người thuê không xem thống kê tài chính.
     */
    private List<Property> ownerScope(UUID propertyId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedPropertyAccessException("Authentication required");
        }
        if (user.role() != UserRole.OWNER && user.role() != UserRole.ADMIN) {
            throw new UnauthorizedPropertyAccessException("Chỉ chủ nhà trọ hoặc quản trị viên được xem thống kê.");
        }
        if (propertyId != null) {
            authorizationService.assertOwnerOrAdmin(user, propertyId);
            return List.of(propertyRepository.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Không tìm thấy nhà trọ: " + propertyId)));
        }
        return user.role() == UserRole.ADMIN ? propertyRepository.findAll() : propertyRepository.findByOwnerId(user.id());
    }

    private List<MaintenanceAggregate> completedMaintenance(Collection<UUID> ids, StatsQuery query) {
        return statsQuery.completedMaintenance(ids, startOf(query.from()), startOf(query.to().plusMonths(1)));
    }

    private static RevenueTotals revenue(List<InvoiceAggregate> rows) {
        BigDecimal billed = sum(rows, InvoiceAggregate::billed);
        BigDecimal collected = sum(rows, InvoiceAggregate::collected);
        return new RevenueTotals(
            rows.stream().mapToLong(InvoiceAggregate::invoiceCount).sum(),
            billed,
            collected,
            sum(rows, InvoiceAggregate::outstanding),
            rows.stream().mapToLong(InvoiceAggregate::overdueCount).sum(),
            sum(rows, InvoiceAggregate::overdueAmount),
            percent(collected, billed));
    }

    private static Occupancy occupancy(List<RoomStatusCount> rows) {
        Map<RoomStatus, Long> counts = zeroed(RoomStatus.class);
        rows.forEach(r -> counts.merge(r.status(), r.count(), Long::sum));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long occupied = counts.get(RoomStatus.OCCUPIED);
        return new Occupancy(total, occupied, counts.get(RoomStatus.RESERVED), counts.get(RoomStatus.MAINTENANCE),
            counts.get(RoomStatus.AVAILABLE), percent(BigDecimal.valueOf(occupied), BigDecimal.valueOf(total)));
    }

    private static MaintenanceStats maintenance(
        List<OpenMaintenanceCount> open, List<RoomStatusCount> rooms, List<MaintenanceAggregate> completed
    ) {
        long completedCount = completed.stream().mapToLong(MaintenanceAggregate::completedCount).sum();
        double hours = completed.stream().mapToDouble(MaintenanceAggregate::totalResolutionHours).sum();
        return new MaintenanceStats(
            open.stream().mapToLong(OpenMaintenanceCount::openCount).sum(),
            open.stream().mapToLong(OpenMaintenanceCount::overdueCount).sum(),
            rooms.stream().filter(r -> r.status() == RoomStatus.MAINTENANCE).mapToLong(RoomStatusCount::count).sum(),
            completedCount,
            sum(completed, MaintenanceAggregate::ownerRepairCost),
            completedCount == 0 ? null : BigDecimal.valueOf(hours / completedCount).setScale(1, RoundingMode.HALF_UP));
    }

    /** Khóa kỳ: 2026-03 (tháng), 2026-Q1 (quý), 2026 (năm). */
    static String periodKey(YearMonth month, Granularity granularity) {
        return switch (granularity) {
            case MONTH -> month.toString();
            case QUARTER -> month.getYear() + "-Q" + ((month.getMonthValue() - 1) / 3 + 1);
            case YEAR -> Integer.toString(month.getYear());
        };
    }

    private static List<String> periodKeys(StatsQuery query) {
        Map<String, Boolean> keys = new LinkedHashMap<>();
        for (YearMonth month = query.from(); !month.isAfter(query.to()); month = month.plusMonths(1)) {
            keys.putIfAbsent(periodKey(month, query.groupBy()), Boolean.TRUE);
        }
        return List.copyOf(keys.keySet());
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return null;
        }
        return part.multiply(HUNDRED).divide(whole, 1, RoundingMode.HALF_UP);
    }

    private static <T> BigDecimal sum(Collection<T> rows, Function<T, BigDecimal> value) {
        return rows.stream().map(value).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static <E extends Enum<E>> Map<E, Long> zeroed(Class<E> type) {
        Map<E, Long> map = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            map.put(value, 0L);
        }
        return map;
    }

    private static List<UUID> ids(List<Property> properties) {
        return properties.stream().map(Property::id).toList();
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(StatisticsQueryPort.ZONE));
    }

    private static Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(StatisticsQueryPort.ZONE).toInstant();
    }
}
