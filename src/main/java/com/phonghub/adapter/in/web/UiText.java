package com.phonghub.adapter.in.web;

import com.phonghub.domain.model.AssetEventType;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.Invoice;
import com.phonghub.domain.model.InvoiceStatus;
import com.phonghub.domain.model.InvoiceType;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceCause;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.User;
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

    public String assetStatus(AssetStatus status) {
        if (status == null) {
            return "Chưa xác định";
        }
        return switch (status) {
            case GOOD -> "Tốt";
            case DAMAGED -> "Hư hỏng";
            case LOST -> "Mất";
            case RETIRED -> "Ngừng sử dụng";
        };
    }

    public String assetEventType(AssetEventType type) {
        if (type == null) {
            return "Chưa xác định";
        }
        return switch (type) {
            case DAMAGED -> "Hư hỏng";
            case REPAIRED -> "Đã sửa";
            case REPLACED -> "Đã thay mới";
            case LOST_REPORTED -> "Báo mất";
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
        if (invoice.isConsolidated()) {
            return "Đã gộp vào hóa đơn tháng";
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
        if (invoice == null || invoice.isConsolidated()
            || invoice.getStatus() == InvoiceStatus.VOIDED || invoice.getStatus() == InvoiceStatus.DRAFT) {
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

    public String invoiceLabel(Invoice invoice) {
        if (invoice == null) {
            return "Chưa xác định";
        }
        if (invoice.getType() == InvoiceType.MAINTENANCE) {
            return "Phí sửa chữa";
        }
        boolean hasFees = invoice.getItems().stream().anyMatch(item -> item.sourceInvoiceId() != null);
        return String.format("Tiền thuê %02d/%d", invoice.getMonth(), invoice.getYear())
            + (hasFees ? " và phí sửa chữa" : "");
    }

    public String maintenanceCause(MaintenanceCause cause) {
        if (cause == null) {
            return "Chưa khai";
        }
        return switch (cause) {
            case NATURAL_WEAR -> "Hư hao tự nhiên / thiết bị cũ";
            case INFRASTRUCTURE -> "Hạ tầng / kết cấu";
            case TENANT_USAGE -> "Do sử dụng của người thuê";
            case UNKNOWN -> "Chưa rõ nguyên nhân";
        };
    }

    public String liableParty(LiableParty party) {
        if (party == null) {
            return "Chưa xác định";
        }
        return switch (party) {
            case OWNER -> "Chủ trọ";
            case TENANT -> "Người thuê";
            case UNDETERMINED -> "Chưa xác định";
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
            case AWAITING_PAYMENT -> "Chờ thanh toán";
            case RESOLVED -> "Đã xử lý";
            case VERIFIED -> "Đã xác nhận";
            case REJECTED -> "Đã từ chối";
        };
    }

    public String userStatus(User.UserStatus status) {
        if (status == null) {
            return "Chưa xác định";
        }
        return switch (status) {
            case ACTIVE -> "Đang hoạt động";
            case INACTIVE -> "Ngừng hoạt động";
            case SUSPENDED -> "Tạm khóa";
        };
    }

    /** Loại đối tượng trong nhật ký; mã lạ (sự kiện mới chưa có nhãn) hiện nguyên mã. */
    public String auditTarget(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return "—";
        }
        return switch (targetType) {
            case "USER" -> "Tài khoản";
            case "PROPERTY" -> "Nhà trọ";
            case "ROOM" -> "Phòng";
            case "CONTRACT" -> "Hợp đồng";
            case "CONTRACT_OCCUPANT" -> "Người ở cùng";
            case "TENANT" -> "Người thuê";
            case "TENANT_DOCUMENT" -> "Giấy tờ người thuê";
            case "MAINTENANCE_TICKET" -> "Phiếu bảo trì";
            case "ROOM_ASSET" -> "Tài sản phòng";
            case "HANDOVER" -> "Biên bản bàn giao";
            case "SERVICE_RATE" -> "Đơn giá dịch vụ";
            case "INVOICE" -> "Hóa đơn";
            case "EXPORT" -> "Xuất dữ liệu";
            default -> targetType;
        };
    }
}
