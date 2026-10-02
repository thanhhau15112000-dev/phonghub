package com.phonghub.adapter.in.web;

import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Bộ lọc danh sách phiếu bảo trì: tab (đang xử lý / đã hoàn thành), trạng thái (chỉ trong tab đang xử lý,
 * gồm "chờ thanh toán"), cơ sở, mức độ, khoảng thời gian báo cáo. Giá trị không hợp lệ bị bỏ qua.
 */
public record MaintenanceListFilter(
    Tab tab,
    MaintenanceStatus status,
    UUID propertyId,
    MaintenancePriority priority,
    LocalDate from,
    LocalDate to
) {

    public enum Tab { OPEN, DONE }

    /** Các trạng thái chưa hoàn thành, theo thứ tự hiển thị trong bộ lọc. */
    public static final List<MaintenanceStatus> OPEN_STATUSES = List.of(
        MaintenanceStatus.REPORTED,
        MaintenanceStatus.ASSIGNED,
        MaintenanceStatus.IN_PROGRESS,
        MaintenanceStatus.AWAITING_PAYMENT
    );

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static MaintenanceListFilter parse(
        String tab, String status, String propertyId, String priority, String from, String to
    ) {
        Tab parsedTab = "done".equalsIgnoreCase(tab) ? Tab.DONE : Tab.OPEN;
        MaintenanceStatus parsedStatus = parseEnum(MaintenanceStatus.class, status);
        if (parsedTab == Tab.DONE || parsedStatus == null || !OPEN_STATUSES.contains(parsedStatus)) {
            parsedStatus = null;
        }
        return new MaintenanceListFilter(
            parsedTab,
            parsedStatus,
            parseUuid(propertyId),
            parseEnum(MaintenancePriority.class, priority),
            parseDate(from),
            parseDate(to)
        );
    }

    /** Phiếu đã hoàn thành hoặc đã đóng (kể cả bị từ chối). Chờ thanh toán chưa phải hoàn thành. */
    public static boolean isDone(MaintenanceTicket ticket) {
        return ticket.getStatus() == MaintenanceStatus.RESOLVED
            || ticket.getStatus() == MaintenanceStatus.VERIFIED
            || ticket.getStatus() == MaintenanceStatus.REJECTED;
    }

    /** Điều kiện chung của cả hai tab: cơ sở, mức độ, ngày báo cáo (theo giờ Việt Nam, gồm cả hai đầu mút). */
    public boolean matchesCommon(MaintenanceTicket ticket) {
        if (propertyId != null && !propertyId.equals(ticket.getPropertyId())) {
            return false;
        }
        if (priority != null && priority != ticket.getPriority()) {
            return false;
        }
        LocalDate reported = ticket.getCreatedAt().atZone(ZONE).toLocalDate();
        if (from != null && reported.isBefore(from)) {
            return false;
        }
        return to == null || !reported.isAfter(to);
    }

    public boolean matchesTab(MaintenanceTicket ticket) {
        if (tab == Tab.DONE) {
            return isDone(ticket);
        }
        return !isDone(ticket) && (status == null || ticket.getStatus() == status);
    }

    /** Query string (đã mã hóa) của các điều kiện chung, để giữ nguyên khi đổi tab hoặc trạng thái. */
    public String commonQuery() {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        if (propertyId != null) {
            builder.queryParam("propertyId", propertyId);
        }
        if (priority != null) {
            builder.queryParam("priority", priority.name());
        }
        if (from != null) {
            builder.queryParam("from", from);
        }
        if (to != null) {
            builder.queryParam("to", to);
        }
        String query = builder.build().encode().getQuery();
        return query == null ? "" : query;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
