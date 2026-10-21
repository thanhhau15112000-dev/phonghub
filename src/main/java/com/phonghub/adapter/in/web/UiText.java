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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
            case CANCELLED -> "Đã hủy";
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
            case CANCELLED -> "Đã hủy";
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

    /** Loại dữ liệu của sự kiện xuất (mã đối tượng của EXPORT). */
    public String exportDataset(String dataset) {
        if (dataset == null) {
            return "—";
        }
        return switch (dataset) {
            case "INVOICES" -> "Hóa đơn";
            case "DEBTS" -> "Công nợ";
            case "CONTRACTS" -> "Hợp đồng";
            default -> dataset;
        };
    }

    /** Hành động trong nhật ký; mã lạ (sự kiện mới chưa có nhãn) hiện nguyên mã. */
    public String auditAction(String action) {
        if (action == null || action.isBlank()) {
            return "—";
        }
        return AUDIT_ACTIONS.getOrDefault(action, action);
    }

    public boolean isKnownAuditAction(String action) {
        return action != null && AUDIT_ACTIONS.containsKey(action);
    }

    /** Hành động cho bộ lọc nhật ký, theo nhóm: tên nhóm → (mã → nhãn). */
    public Map<String, Map<String, String>> auditActionGroups() {
        return AUDIT_ACTION_GROUPS;
    }

    private static final Map<String, Map<String, String>> AUDIT_ACTION_GROUPS = buildAuditActionGroups();

    private static final Map<String, String> AUDIT_ACTIONS = AUDIT_ACTION_GROUPS.values().stream()
        .flatMap(group -> group.entrySet().stream())
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    private static Map<String, Map<String, String>> buildAuditActionGroups() {
        Map<String, Map<String, String>> groups = new LinkedHashMap<>();
        groups.put("Tài khoản", labels(
            "LOGIN_SUCCEEDED", "Đăng nhập thành công",
            "LOGIN_FAILED", "Đăng nhập thất bại",
            "USER_CHANGE_PASSWORD", "Đổi mật khẩu",
            "ADMIN_RESET_PASSWORD", "Admin đặt lại mật khẩu",
            "ADMIN_CREATE_USER", "Admin tạo tài khoản",
            "USER_UPDATED_BY_ADMIN", "Admin sửa tài khoản",
            "ADMIN_DELETE_USER", "Admin xóa tài khoản"));
        groups.put("Nhà trọ và nhân sự", labels(
            "PROPERTY_UPDATED", "Sửa nhà trọ",
            "ADMIN_VERIFY_PROPERTY", "Duyệt nhà trọ",
            "ADMIN_REJECT_PROPERTY", "Từ chối nhà trọ",
            "PROPERTY_ARCHIVED", "Ẩn nhà trọ",
            "PROPERTY_RESTORED", "Khôi phục nhà trọ",
            "HOUSE_RULES_UPDATED", "Sửa nội quy",
            "LATE_FEE_POLICY_UPDATED", "Sửa chính sách phí trễ hạn",
            "STAFF_ASSIGNED", "Thêm nhân sự",
            "STAFF_PERMISSIONS_UPDATED", "Sửa quyền nhân sự",
            "STAFF_UNASSIGNED", "Gỡ nhân sự"));
        groups.put("Phòng và tài sản", labels(
            "ROOM_CREATED", "Tạo phòng",
            "ROOM_UPDATED", "Sửa phòng",
            "ROOM_STATUS_CHANGED", "Đổi trạng thái phòng",
            "ROOM_ARCHIVED", "Ngừng sử dụng phòng",
            "ROOM_RESTORED", "Dùng lại phòng",
            "ROOM_ASSET_CREATED", "Thêm tài sản",
            "ROOM_ASSET_BATCH_CREATED", "Thêm nhiều tài sản",
            "ROOM_ASSET_TEMPLATE_APPLIED", "Áp mẫu tài sản",
            "ROOM_ASSET_UPDATED", "Sửa tài sản",
            "ROOM_ASSET_DELETED", "Xóa tài sản",
            "ROOM_ASSET_EVENT_RECORDED", "Ghi sự cố tài sản"));
        groups.put("Điện nước", labels(
            "SERVICE_RATE_CREATED", "Thêm đơn giá dịch vụ",
            "SERVICE_RATE_UPDATED", "Sửa đơn giá dịch vụ",
            "METER_READING_RECORDED", "Ghi chỉ số đồng hồ",
            "METER_OCR_REQUESTED", "Đọc chỉ số bằng OCR",
            "METER_PHOTO_ATTACHED", "Đính ảnh đồng hồ",
            "METER_SUBMISSION_CREATED", "Người thuê gửi chỉ số",
            "METER_SUBMISSION_ACCEPTED", "Duyệt chỉ số người thuê gửi",
            "METER_SUBMISSION_REJECTED", "Từ chối chỉ số người thuê gửi"));
        groups.put("Hợp đồng", labels(
            "CONTRACT_CREATED", "Tạo hợp đồng",
            "CONTRACT_UPDATED", "Sửa hợp đồng nháp",
            "CONTRACT_CANCELLED", "Hủy hợp đồng nháp",
            "CONTRACT_ACTIVATED", "Kích hoạt hợp đồng",
            "CONTRACT_TERMINATED", "Chấm dứt hợp đồng",
            "CONTRACT_EXPIRED", "Hợp đồng hết hạn",
            "OCCUPANT_CHECKOUT", "Người ở cùng trả phòng"));
        Map<String, String> handover = new LinkedHashMap<>();
        handoverSteps("HANDOVER_", "Nhận phòng", handover);
        handoverSteps("CHECKOUT_", "Trả phòng", handover);
        handover.put("HANDOVER_PDF_EXPORTED", "Xuất PDF biên bản");
        groups.put("Biên bản bàn giao", handover);
        groups.put("Bảo trì", labels(
            "MAINTENANCE_TECHNICIAN_ASSIGNED", "Phân công kỹ thuật viên",
            "MAINTENANCE_TICKET_UPDATED", "Sửa phiếu bảo trì",
            "MAINTENANCE_TICKET_CANCELLED", "Hủy phiếu bảo trì",
            "MAINTENANCE_TICKET_REJECTED", "Từ chối phiếu bảo trì",
            "MAINTENANCE_LIABLE_PARTY_CHANGED", "Đổi bên chịu phí",
            "MAINTENANCE_QUOTED", "Báo giá sửa chữa",
            "MAINTENANCE_FEE_ISSUED", "Thu phí sửa chữa",
            "MAINTENANCE_FEE_WAIVED", "Miễn phí sửa chữa",
            "MAINTENANCE_VERIFIED", "Xác nhận đã sửa xong",
            "MAINTENANCE_REOPENED_BY_TENANT", "Người thuê mở lại phiếu",
            "MAINTENANCE_SLA_BREACHED", "Quá hạn xử lý",
            "MAINTENANCE_ATTACHMENT_ADDED", "Thêm ảnh/video bảo trì",
            "MAINTENANCE_ATTACHMENT_REMOVED", "Xóa ảnh/video bảo trì"));
        groups.put("Hóa đơn", labels(
            "LATE_FEE_APPLIED", "Cộng phí trễ hạn",
            "INVOICE_VOIDED", "Hủy hóa đơn"));
        groups.put("Người thuê và giấy tờ", labels(
            "TENANT_CREATED", "Thêm người thuê",
            "TENANT_UPDATE", "Sửa người thuê",
            "TENANT_HIDDEN", "Ẩn người thuê",
            "TENANT_RESTORED", "Khôi phục người thuê",
            "TENANT_DOCUMENTS_VIEWED", "Xem danh sách giấy tờ",
            "TENANT_DOCUMENT_VIEWED", "Xem giấy tờ",
            "TENANT_DOCUMENT_UPLOADED", "Tải lên giấy tờ",
            "TENANT_DOCUMENT_REVEALED", "Xem số giấy tờ đầy đủ",
            "TENANT_DOCUMENT_REVEAL_FAILED", "Xem số giấy tờ bị từ chối (sai mật khẩu)",
            "TENANT_DOCUMENT_DELETED", "Xóa giấy tờ",
            "TENANT_LEGAL_HOLD_SET", "Giữ dữ liệu theo yêu cầu pháp lý",
            "TENANT_LEGAL_HOLD_CLEARED", "Bỏ giữ dữ liệu",
            "TENANT_ERASURE_REQUESTED", "Yêu cầu xóa dữ liệu",
            "TENANT_PURGE_CONFIRM_FAILED", "Xác nhận xóa dữ liệu bị từ chối (sai mật khẩu)",
            "TENANT_DATA_PURGED", "Đã xóa dữ liệu người thuê"));
        groups.put("Xuất dữ liệu", labels("DATA_EXPORTED", "Xuất Excel/PDF"));
        return Collections.unmodifiableMap(groups);
    }

    private static void handoverSteps(String prefix, String kind, Map<String, String> into) {
        Map<String, String> steps = labels(
            "CREATED", "tạo biên bản",
            "UPDATED", "sửa biên bản",
            "ITEM_UPDATED", "sửa hạng mục",
            "PHOTO_ADDED", "thêm ảnh",
            "PHOTO_REMOVED", "xóa ảnh",
            "SENT", "gửi người thuê xác nhận",
            "TENANT_CONFIRMED", "người thuê xác nhận",
            "APPROVED", "duyệt biên bản",
            "REJECTED", "từ chối biên bản",
            "REOPENED", "mở lại biên bản");
        steps.forEach((step, label) -> into.put(prefix + step, kind + ": " + label));
    }

    private static Map<String, String> labels(String... codeAndLabel) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < codeAndLabel.length; i += 2) {
            map.put(codeAndLabel[i], codeAndLabel[i + 1]);
        }
        return map;
    }
}
