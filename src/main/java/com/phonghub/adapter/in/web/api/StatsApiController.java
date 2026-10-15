package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.StatisticsUseCase;
import com.phonghub.application.port.in.StatisticsUseCase.AccountStats;
import com.phonghub.application.port.in.StatisticsUseCase.AdminOverview;
import com.phonghub.application.port.in.StatisticsUseCase.Granularity;
import com.phonghub.application.port.in.StatisticsUseCase.OwnerSummary;
import com.phonghub.application.port.in.StatisticsUseCase.PendingProperty;
import com.phonghub.application.port.in.StatisticsUseCase.PeriodStat;
import com.phonghub.application.port.in.StatisticsUseCase.PropertyStat;
import com.phonghub.application.port.in.StatisticsUseCase.StatsQuery;
import com.phonghub.application.port.out.StatisticsQueryPort;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thống kê (#93). Khoảng thời gian dạng {@code yyyy-MM} (bao gồm hai đầu), mặc định 12 tháng gần nhất. Phân quyền
 * nằm ở {@link StatisticsUseCase}: owner chỉ nhà của mình, admin mọi nhà; nhóm {@code /admin} chỉ dành cho admin.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsApiController {

    private final StatisticsUseCase statisticsUseCase;
    private final Clock clock;

    @Autowired
    public StatsApiController(StatisticsUseCase statisticsUseCase) {
        this(statisticsUseCase, Clock.systemUTC());
    }

    StatsApiController(StatisticsUseCase statisticsUseCase, Clock clock) {
        this.statisticsUseCase = statisticsUseCase;
        this.clock = clock;
    }

    @GetMapping("/owner/summary")
    public ResponseEntity<OwnerSummary> ownerSummary(
        @RequestParam(required = false) UUID propertyId,
        @RequestParam(required = false) YearMonth from,
        @RequestParam(required = false) YearMonth to
    ) {
        return ResponseEntity.ok(statisticsUseCase.ownerSummary(query(propertyId, from, to, null)));
    }

    @GetMapping("/owner/revenue")
    public ResponseEntity<List<PeriodStat>> ownerRevenue(
        @RequestParam(required = false) UUID propertyId,
        @RequestParam(required = false) YearMonth from,
        @RequestParam(required = false) YearMonth to,
        @RequestParam(required = false) Granularity groupBy
    ) {
        return ResponseEntity.ok(statisticsUseCase.ownerRevenue(query(propertyId, from, to, groupBy)));
    }

    @GetMapping("/owner/properties")
    public ResponseEntity<List<PropertyStat>> ownerProperties(
        @RequestParam(required = false) UUID propertyId,
        @RequestParam(required = false) YearMonth from,
        @RequestParam(required = false) YearMonth to
    ) {
        return ResponseEntity.ok(statisticsUseCase.ownerProperties(query(propertyId, from, to, null)));
    }

    @GetMapping("/admin/overview")
    public ResponseEntity<AdminOverview> adminOverview(
        @RequestParam(required = false) YearMonth from,
        @RequestParam(required = false) YearMonth to
    ) {
        return ResponseEntity.ok(statisticsUseCase.adminOverview(query(null, from, to, null)));
    }

    @GetMapping("/admin/pending-properties")
    public ResponseEntity<List<PendingProperty>> pendingProperties() {
        return ResponseEntity.ok(statisticsUseCase.pendingProperties());
    }

    @GetMapping("/admin/accounts")
    public ResponseEntity<AccountStats> adminAccounts(
        @RequestParam(required = false) YearMonth from,
        @RequestParam(required = false) YearMonth to
    ) {
        return ResponseEntity.ok(statisticsUseCase.adminAccounts(query(null, from, to, null)));
    }

    private StatsQuery query(UUID propertyId, YearMonth from, YearMonth to, Granularity groupBy) {
        return StatsQuery.of(propertyId, from, to, groupBy, YearMonth.now(clock.withZone(StatisticsQueryPort.ZONE)));
    }
}
