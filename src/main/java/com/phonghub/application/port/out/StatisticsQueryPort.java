package com.phonghub.application.port.out;

import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Truy vấn tổng hợp cho thống kê (#93). Mỗi phương thức là một truy vấn GROUP BY trên toàn phạm vi, không lặp
 * theo từng nhà trọ/hóa đơn (tránh N+1). Tham số {@code propertyIds} null nghĩa là toàn hệ thống (chỉ dùng cho
 * admin); danh sách rỗng trả kết quả rỗng.
 */
public interface StatisticsQueryPort {

    /** Tháng được tính theo giờ Việt Nam để khớp với kỳ hóa đơn và lịch chạy job. */
    ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Hóa đơn theo nhà trọ + kỳ (năm/tháng của hóa đơn) trong [from, to]. Bỏ hóa đơn VOIDED và khoản phí đã gộp
     * vào hóa đơn tháng (đã nằm trong hóa đơn tháng, tính lại sẽ trùng).
     */
    List<InvoiceAggregate> invoiceAggregates(Collection<UUID> propertyIds, YearMonth from, YearMonth to, LocalDate today);

    /** Số phòng theo nhà trọ + trạng thái hiện tại. */
    List<RoomStatusCount> roomStatusCounts(Collection<UUID> propertyIds);

    /**
     * Phiếu bảo trì hoàn thành (lần đầu chuyển sang RESOLVED/AWAITING_PAYMENT) trong [from, to), gom theo nhà trọ +
     * tháng hoàn thành. Chi phí chủ chịu là repair_cost của phiếu không do người thuê chịu.
     */
    List<MaintenanceAggregate> completedMaintenance(Collection<UUID> propertyIds, Instant from, Instant to);

    /** Phiếu chưa xử lý xong theo nhà trọ, kèm số phiếu đã quá hạn SLA tại thời điểm {@code now}. */
    List<OpenMaintenanceCount> openMaintenance(Collection<UUID> propertyIds, Instant now);

    /** Số nhà trọ theo trạng thái xác minh trên toàn hệ thống. */
    List<ApprovalCount> propertyApprovalCounts();

    /** Nhà trọ đang chờ duyệt, cũ nhất trước, kèm thông tin chủ nhà (một truy vấn JOIN). */
    List<PendingPropertyRow> pendingProperties();

    /** Số tài khoản theo vai trò + trạng thái. */
    List<UserCount> userCounts();

    /** Tài khoản tạo mới trong [from, to) theo tháng + vai trò. */
    List<SignupCount> userSignups(Instant from, Instant to);

    /** Hoạt động ghi nhận trong audit log trong [from, to) theo tháng: số thao tác và số người thao tác. */
    List<ActivityCount> auditActivity(Instant from, Instant to);

    record InvoiceAggregate(
        UUID propertyId,
        YearMonth period,
        long invoiceCount,
        BigDecimal billed,
        BigDecimal collected,
        BigDecimal outstanding,
        long overdueCount,
        BigDecimal overdueAmount
    ) {}

    record RoomStatusCount(UUID propertyId, RoomStatus status, long count) {}

    record MaintenanceAggregate(
        UUID propertyId,
        YearMonth period,
        long completedCount,
        BigDecimal ownerRepairCost,
        double totalResolutionHours
    ) {}

    record OpenMaintenanceCount(UUID propertyId, long openCount, long overdueCount) {}

    record ApprovalCount(PropertyApprovalStatus status, long count) {}

    record PendingPropertyRow(
        UUID id,
        String name,
        String address,
        UUID ownerId,
        String ownerName,
        String ownerEmail,
        Instant createdAt
    ) {}

    record UserCount(UserRole role, User.UserStatus status, long count) {}

    record SignupCount(YearMonth period, UserRole role, long count) {}

    record ActivityCount(YearMonth period, long actions, long activeUsers) {}
}
