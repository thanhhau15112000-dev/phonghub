package com.phonghub.adapter.in.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.phonghub.application.port.in.StatisticsUseCase;
import com.phonghub.application.port.in.StatisticsUseCase.Granularity;
import com.phonghub.application.port.in.StatisticsUseCase.StatsQuery;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** #93: controller chỉ dựng khoảng thời gian và chuyển cho use case; phân quyền không nằm ở đây. */
class StatsApiControllerTest {

    // 30/09 18:00 UTC đã là 01/10 giờ Việt Nam: tháng hiện tại phải là 2026-10.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T18:00:00Z"), ZoneOffset.UTC);

    private StatisticsUseCase useCase;
    private StatsApiController controller;

    @BeforeEach
    void setUp() {
        useCase = mock(StatisticsUseCase.class);
        controller = new StatsApiController(useCase, CLOCK);
    }

    private StatsQuery captured() {
        ArgumentCaptor<StatsQuery> captor = ArgumentCaptor.forClass(StatsQuery.class);
        verify(useCase).ownerRevenue(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("mặc định 12 tháng gần nhất theo giờ Việt Nam, nhóm theo tháng")
    void defaultRange() {
        when(useCase.ownerRevenue(any())).thenReturn(List.of());

        controller.ownerRevenue(null, null, null, null);

        StatsQuery query = captured();
        assertEquals(YearMonth.of(2025, 11), query.from());
        assertEquals(YearMonth.of(2026, 10), query.to());
        assertEquals(Granularity.MONTH, query.groupBy());
    }

    @Test
    @DisplayName("chuyển nguyên nhà trọ, khoảng và cách nhóm sang use case")
    void passesParameters() {
        UUID propertyId = UUID.randomUUID();
        when(useCase.ownerRevenue(any())).thenReturn(List.of());

        controller.ownerRevenue(propertyId, YearMonth.of(2026, 1), YearMonth.of(2026, 6), Granularity.QUARTER);

        StatsQuery query = captured();
        assertEquals(propertyId, query.propertyId());
        assertEquals(YearMonth.of(2026, 1), query.from());
        assertEquals(YearMonth.of(2026, 6), query.to());
        assertEquals(Granularity.QUARTER, query.groupBy());
    }

    @Test
    @DisplayName("khoảng sai bị chặn trước khi gọi use case (trả 400)")
    void invalidRangeRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> controller.ownerSummary(null, YearMonth.of(2026, 6), YearMonth.of(2026, 1)));
        assertThrows(IllegalArgumentException.class,
            () -> controller.adminAccounts(YearMonth.of(2010, 1), YearMonth.of(2026, 1)));
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("các endpoint admin không truyền propertyId; lỗi phân quyền từ use case giữ nguyên để trả 403")
    void adminEndpoints() {
        var overview = mock(StatisticsUseCase.AdminOverview.class);
        when(useCase.adminOverview(any())).thenReturn(overview);
        assertSame(overview, controller.adminOverview(null, null).getBody());
        ArgumentCaptor<StatsQuery> captor = ArgumentCaptor.forClass(StatsQuery.class);
        verify(useCase).adminOverview(captor.capture());
        assertEquals(null, captor.getValue().propertyId());

        when(useCase.pendingProperties()).thenThrow(new UnauthorizedPropertyAccessException("no"));
        assertThrows(UnauthorizedPropertyAccessException.class, () -> controller.pendingProperties());
    }
}
