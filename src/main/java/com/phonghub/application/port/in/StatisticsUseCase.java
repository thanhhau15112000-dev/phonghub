package com.phonghub.application.port.in;

import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thống kê doanh thu, chi phí, công nợ, lấp đầy (owner) và tổng quan hệ thống (admin) — #93, dùng cho dashboard #75.
 *
 * <p>Quy ước số liệu: doanh thu tính theo kỳ của hóa đơn (phát sinh = tổng tiền hóa đơn, đã thu = số đã trả của các
 * hóa đơn đó), bỏ hóa đơn đã hủy; chi phí là phí sửa chữa chủ nhà chịu của phiếu hoàn thành trong kỳ; lợi nhuận ước
 * tính = đã thu − chi phí. Tỷ lệ lấp đầy là ảnh chụp hiện tại.
 */
public interface StatisticsUseCase {

    /** Owner: nhà của mình (hoặc một nhà qua propertyId); admin: toàn hệ thống hoặc một nhà. Vai trò khác bị 403. */
    OwnerSummary ownerSummary(StatsQuery query);

    /** Chuỗi doanh thu/chi phí theo tháng, quý hoặc năm, đủ mọi kỳ trong khoảng (kỳ không có dữ liệu = 0). */
    List<PeriodStat> ownerRevenue(StatsQuery query);

    /** Số liệu từng nhà trọ trong phạm vi. */
    List<PropertyStat> ownerProperties(StatsQuery query);

    /** Admin: tổng quan toàn hệ thống. */
    AdminOverview adminOverview(StatsQuery query);

    /** Admin: nhà trọ chờ duyệt, cũ nhất trước. */
    List<PendingProperty> pendingProperties();

    /** Admin: tài khoản theo vai trò/trạng thái và hoạt động theo tháng. */
    AccountStats adminAccounts(StatsQuery query);

    enum Granularity { MONTH, QUARTER, YEAR }

    /**
     * Khoảng kỳ [from, to] tính theo tháng (bao gồm hai đầu). Tối đa {@link #MAX_MONTHS} tháng để truy vấn không
     * quét quá rộng.
     */
    record StatsQuery(UUID propertyId, YearMonth from, YearMonth to, Granularity groupBy) {
        public static final int MAX_MONTHS = 60;
        public static final int DEFAULT_MONTHS = 12;

        public StatsQuery {
            if (from == null || to == null) {
                throw new IllegalArgumentException("Khoảng thời gian thống kê không được để trống.");
            }
            if (from.isAfter(to)) {
                throw new IllegalArgumentException("Tháng bắt đầu không được sau tháng kết thúc.");
            }
            if (ChronoUnit.MONTHS.between(from, to) + 1 > MAX_MONTHS) {
                throw new IllegalArgumentException("Khoảng thống kê tối đa " + MAX_MONTHS + " tháng.");
            }
            if (groupBy == null) {
                groupBy = Granularity.MONTH;
            }
        }

        /** Thiếu {@code to} thì lấy tháng hiện tại; thiếu {@code from} thì lùi {@value #DEFAULT_MONTHS} tháng. */
        public static StatsQuery of(UUID propertyId, YearMonth from, YearMonth to, Granularity groupBy, YearMonth current) {
            YearMonth end = to != null ? to : (from != null && from.isAfter(current) ? from : current);
            YearMonth start = from != null ? from : end.minusMonths(DEFAULT_MONTHS - 1);
            return new StatsQuery(propertyId, start, end, groupBy);
        }
    }

    /** Doanh thu và công nợ. {@code collectionRate} là % đã thu trên phát sinh, null khi chưa phát sinh. */
    record RevenueTotals(
        long invoiceCount,
        BigDecimal billed,
        BigDecimal collected,
        BigDecimal outstanding,
        long overdueInvoices,
        BigDecimal overdueAmount,
        BigDecimal collectionRate
    ) {}

    /** {@code occupancyRate} là % phòng đang ở trên tổng số phòng, null khi chưa có phòng. */
    record Occupancy(long totalRooms, long occupied, long reserved, long maintenance, long available,
                     BigDecimal occupancyRate) {}

    /** {@code avgResolutionHours}: thời gian trung bình từ lúc báo đến lúc sửa xong của phiếu hoàn thành trong kỳ. */
    record MaintenanceStats(long openTickets, long overdueTickets, long roomsUnderMaintenance, long completedTickets,
                            BigDecimal repairCost, BigDecimal avgResolutionHours) {}

    record OwnerSummary(
        String from,
        String to,
        UUID propertyId,
        int propertyCount,
        RevenueTotals revenue,
        BigDecimal repairCost,
        BigDecimal estimatedProfit,
        Occupancy occupancy,
        MaintenanceStats maintenance
    ) {}

    record PeriodStat(String period, BigDecimal billed, BigDecimal collected, BigDecimal outstanding,
                      BigDecimal repairCost, BigDecimal estimatedProfit) {}

    record PropertyStat(
        UUID propertyId,
        String name,
        PropertyApprovalStatus approvalStatus,
        RevenueTotals revenue,
        BigDecimal repairCost,
        BigDecimal estimatedProfit,
        Occupancy occupancy
    ) {}

    record AdminOverview(
        String from,
        String to,
        long properties,
        Map<PropertyApprovalStatus, Long> propertiesByApproval,
        Map<UserRole, Long> accountsByRole,
        RevenueTotals revenue,
        Occupancy occupancy
    ) {
        /** Số nhà trọ theo tên trạng thái xác minh (PENDING, VERIFIED, REJECTED), tiện cho template. */
        public long propertiesWithStatus(String status) {
            return propertiesByApproval.getOrDefault(PropertyApprovalStatus.valueOf(status), 0L);
        }

        /** Số tài khoản theo tên vai trò, tiện cho template. */
        public long accounts(String role) {
            return accountsByRole.getOrDefault(UserRole.valueOf(role), 0L);
        }
    }

    record PendingProperty(UUID id, String name, String address, UUID ownerId, String ownerName, String ownerEmail,
                           Instant createdAt) {}

    record AccountStats(
        String from,
        String to,
        long totalAccounts,
        Map<UserRole, Long> byRole,
        Map<User.UserStatus, Long> byStatus,
        List<ActivityPeriod> activity
    ) {}

    /** Một tháng: tài khoản mới theo vai trò, số thao tác ghi audit và số người có thao tác. */
    record ActivityPeriod(String period, long newAccounts, Map<UserRole, Long> newAccountsByRole, long actions,
                          long activeUsers) {}
}
