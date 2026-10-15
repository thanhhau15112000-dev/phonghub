package com.phonghub.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.in.StatisticsUseCase;
import com.phonghub.application.port.in.StatisticsUseCase.StatsQuery;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.UserRole;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #88: decorator cache khóa theo người dùng và xóa cache khi ghi (kể cả khi ghi lỗi giữa chừng). */
class CachingUseCasesUnitTest {

    private final CurrentUser owner1 = new CurrentUser(UUID.randomUUID(), "o1@x", "Owner 1", UserRole.OWNER);
    private final CurrentUser owner2 = new CurrentUser(UUID.randomUUID(), "o2@x", "Owner 2", UserRole.OWNER);
    private final AtomicReference<CurrentUser> caller = new AtomicReference<>(owner1);
    private final PhongHubCaches caches = new PhongHubCaches(true, Duration.ofMinutes(5), 100);

    @Test
    @DisplayName("danh sách nhà trọ cache theo người dùng; sửa nhà trọ (kể cả lỗi) xóa cache")
    void properties() {
        PropertyUseCase delegate = mock(PropertyUseCase.class);
        when(delegate.listAccessibleProperties()).thenReturn(List.of());
        when(delegate.updateProperty(any())).thenThrow(new IllegalStateException("lỗi sau khi đã ghi"));
        CachingPropertyUseCase useCase = new CachingPropertyUseCase(delegate, caches, caller::get);

        useCase.listAccessibleProperties();
        useCase.listAccessibleProperties();
        caller.set(owner2);
        useCase.listAccessibleProperties();
        verify(delegate, times(2)).listAccessibleProperties();

        assertThrows(IllegalStateException.class, () -> useCase.updateProperty(
            new PropertyUseCase.UpdatePropertyCommand(UUID.randomUUID(), "Tên", "Địa chỉ", null, null)));
        useCase.listAccessibleProperties();
        verify(delegate, times(3)).listAccessibleProperties();

        caller.set(null);
        useCase.listAccessibleProperties();
        useCase.listAccessibleProperties();
        verify(delegate, times(5)).listAccessibleProperties();
    }

    @Test
    @DisplayName("danh sách phòng cache theo (người dùng, nhà trọ); 403 không được cache; đổi trạng thái phòng xóa cache")
    void rooms() {
        UUID propertyA = UUID.randomUUID();
        UUID propertyB = UUID.randomUUID();
        RoomUseCase delegate = mock(RoomUseCase.class);
        when(delegate.listRoomsForProperty(propertyA)).thenReturn(List.of());
        when(delegate.listRoomsForProperty(propertyB)).thenThrow(new UnauthorizedPropertyAccessException("403"));
        CachingRoomUseCase useCase = new CachingRoomUseCase(delegate, caches, caller::get);

        useCase.listRoomsForProperty(propertyA);
        useCase.listRoomsForProperty(propertyA);
        verify(delegate, times(1)).listRoomsForProperty(propertyA);
        caller.set(owner2);
        useCase.listRoomsForProperty(propertyA);
        verify(delegate, times(2)).listRoomsForProperty(propertyA);

        assertThrows(UnauthorizedPropertyAccessException.class, () -> useCase.listRoomsForProperty(propertyB));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> useCase.listRoomsForProperty(propertyB));
        verify(delegate, times(2)).listRoomsForProperty(propertyB);

        useCase.changeRoomStatus(UUID.randomUUID(), com.phonghub.domain.model.RoomStatus.MAINTENANCE);
        useCase.listRoomsForProperty(propertyA);
        verify(delegate, times(3)).listRoomsForProperty(propertyA);
    }

    @Test
    @DisplayName("dashboard owner cache theo (người dùng, bộ lọc); thống kê admin không cache")
    void statistics() {
        StatisticsUseCase delegate = mock(StatisticsUseCase.class);
        CachingStatisticsUseCase useCase = new CachingStatisticsUseCase(delegate, caches, caller::get);
        StatsQuery all = new StatsQuery(null, java.time.YearMonth.of(2026, 1), java.time.YearMonth.of(2026, 6), null);
        StatsQuery other = new StatsQuery(null, java.time.YearMonth.of(2026, 1), java.time.YearMonth.of(2026, 3), null);
        when(delegate.ownerRevenue(any())).thenReturn(List.of());
        when(delegate.ownerProperties(any())).thenReturn(List.of());

        useCase.ownerRevenue(all);
        useCase.ownerRevenue(all);
        useCase.ownerRevenue(other);
        verify(delegate, times(1)).ownerRevenue(all);
        verify(delegate, times(1)).ownerRevenue(other);
        useCase.ownerProperties(all);
        useCase.ownerProperties(all);
        verify(delegate, times(1)).ownerProperties(all);

        useCase.adminOverview(all);
        useCase.adminOverview(all);
        useCase.pendingProperties();
        useCase.pendingProperties();
        verify(delegate, times(2)).adminOverview(all);
        verify(delegate, times(2)).pendingProperties();
        assertEquals(3, caches.size());
    }
}
