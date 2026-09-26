package com.phonghub.adapter.in.web;

import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.UserRole;

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
