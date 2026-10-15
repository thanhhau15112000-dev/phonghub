package com.phonghub.config;

import com.phonghub.application.port.in.StatisticsUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import java.util.List;
import java.util.function.Supplier;

/**
 * Cache số liệu dashboard vận hành của owner (#88, #75) theo (người dùng, bộ lọc). Thống kê admin không cache vì có
 * đếm tài khoản — tạo/xóa tài khoản không chạy trong transaction nên không xóa cache kịp.
 */
public class CachingStatisticsUseCase implements StatisticsUseCase {

    private final StatisticsUseCase delegate;
    private final PhongHubCaches caches;
    private final CurrentUserPort currentUserPort;

    public CachingStatisticsUseCase(StatisticsUseCase delegate, PhongHubCaches caches, CurrentUserPort currentUserPort) {
        this.delegate = delegate;
        this.caches = caches;
        this.currentUserPort = currentUserPort;
    }

    @Override
    public OwnerSummary ownerSummary(StatsQuery query) {
        return cached("stats.summary", query, () -> delegate.ownerSummary(query));
    }

    @Override
    public List<PeriodStat> ownerRevenue(StatsQuery query) {
        return cached("stats.revenue", query, () -> delegate.ownerRevenue(query));
    }

    @Override
    public List<PropertyStat> ownerProperties(StatsQuery query) {
        return cached("stats.properties", query, () -> delegate.ownerProperties(query));
    }

    @Override
    public AdminOverview adminOverview(StatsQuery query) {
        return delegate.adminOverview(query);
    }

    @Override
    public List<PendingProperty> pendingProperties() {
        return delegate.pendingProperties();
    }

    @Override
    public AccountStats adminAccounts(StatsQuery query) {
        return delegate.adminAccounts(query);
    }

    private <T> T cached(String area, StatsQuery query, Supplier<T> loader) {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null) {
            return loader.get();
        }
        return caches.get(new PhongHubCaches.Key(area, user.id(), query), loader);
    }
}
