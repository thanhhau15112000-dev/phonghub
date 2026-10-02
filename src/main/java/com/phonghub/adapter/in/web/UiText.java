package com.phonghub.adapter.in.web;

import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.UserRole;
import java.time.LocalDate;

/**
 * Nhãn tiếng Việt dành riêng cho giao diện web.
 * Giá trị enum/API vẫn giữ nguyên tiếng Anh để không thay đổi contract backend.
 */
public final class UiText {

    public static final UiText INSTANCE = new UiText();

    private UiText() {}

    public String role(UserRole role) {
        if (role == null) {
            return "Chưa xác định";
        }
        return switch (role) {
            case ADMIN -> "Quản trị viên";
            case OWNER -> "Chủ trọ";
            case STAFF -> "Nhân viên";
            case TECHNICIAN -> "Kỹ thuật viên";
            case TENANT -> "Người thuê";
        };
    }

    public String approvalStatus(com.phonghub.domain.model.PropertyApprovalStatus status) {
        if (status == null) {
            return "Chưa xác định";
        }
        return switch (status) {
            case PENDING -> "Chờ duyệt";
            case VERIFIED -> "Đã duyệt";
            case REJECTED -> "Bị từ chối";
        };
    }

    public String roomStatus(RoomStatus status) {
        if (status == null) {
            return "Chưa xác định";
        }
        return switch (status) {
            case AVAILABLE -> "Trống";
            case RESERVED -> "Đã đặt";
            case OCCUPIED -> "Đang thuê";
            case MAINTENANCE -> "Bảo trì";
        };
    }

    public String contractStatus(ContractStatus status) {
        if (status == null) {
            return "Chưa xác định";
        }
        return switch (status) {
            case DRAFT -> "Bản nháp";
            case ACTIVE -> "Đang hiệu lực";
            case EXPIRED -> "Đã hết hạn";
            case TERMINATED -> "Đã chấm dứt";
        };
    }

    public String invoiceState(Invoice invoice, LocalDate today) {
        if (invoice == null) {
            return "Chưa xác định";
        }
        return switch (invoice.getStatus()) {
            case PAID -> "Đã thanh toán";
            case VOIDED -> "Đã hủy";
            case DRAFT -> "Bản nháp";
            case ISSUED, PARTIALLY_PAID, OVERDUE -> {
                String state = invoice.isOverdue(today) ? "Quá hạn"
                    : invoice.isDue(today) ? "Đến hạn"
                    : "Chưa đến hạn";
                yield invoice.getStatus() == InvoiceStatus.PARTIALLY_PAID ? state + " (đã trả một phần)" : state;
            }
        };
    }

    /** Hậu tố class badge-* có sẵn trong style.css, tái sử dụng màu theo mức độ. */
    public String invoiceBadge(Invoice invoice, LocalDate today) {
        if (invoice == null || invoice.getStatus() == InvoiceStatus.VOIDED || invoice.getStatus() == InvoiceStatus.DRAFT) {
            return "DRAFT";
        }
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return "ACTIVE";
        }
        if (invoice.isOverdue(today)) {
            return "REJECTED";
        }
        return invoice.isDue(today) ? "PENDING" : "DRAFT";
    }

    public String maintenancePriority(MaintenancePriority priority) {
        if (priority == null) {
            return "Chưa xác định";
        }
        return switch (priority) {
            case LOW -> "Thấp";
            case MEDIUM -> "Trung bình";
            case HIGH -> "Cao";
            case URGENT -> "Khẩn cấp";
        };
    }

    public String maintenanceStatus(MaintenanceStatus status) {
        if (status == null) {
            return "Chưa xác định";
        }
        return switch (status) {
            case REPORTED -> "Đã báo";
            case ASSIGNED -> "Đã phân công";
            case IN_PROGRESS -> "Đang xử lý";
            case RESOLVED -> "Đã xử lý";
            case VERIFIED -> "Đã xác nhận";
            case REJECTED -> "Đã từ chối";
        };
    }
}
