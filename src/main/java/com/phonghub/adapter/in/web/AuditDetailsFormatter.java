package com.phonghub.adapter.in.web;

import com.phonghub.domain.model.AssetEventType;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.ContractStatus;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.PropertyApprovalStatus;
import com.phonghub.domain.model.RoomStatus;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Chi tiết nhật ký (JSON đã lưu) thành các dòng tiếng Việt cho trang Nhật ký (#78): khóa có nhãn, mã trạng thái
 * được dịch, cặp trước/sau chỉ hiện trường thay đổi. Chi tiết không phải JSON (bản ghi lỗi giải mã, bộ nhớ trong)
 * hiện nguyên văn; khóa hoặc mã chưa có nhãn hiện nguyên mã.
 */
public final class AuditDetailsFormatter {

    /** Một dòng chi tiết; {@code label} null khi hiện nguyên văn. */
    public record Line(String label, String value) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UiText TEXT = UiText.INSTANCE;
    private static final String ARROW = " → ";

    private static final Set<String> MONEY_KEYS = Set.of(
        "amount", "totalAmount", "basePrice", "rentAmount", "depositAmount", "pricePerUnit", "capAmount",
        "deduction", "cost");

    private static final Set<String> STATUS_KEYS = Set.of("status", "oldStatus", "newStatus");

    private static final Map<String, String> KEY_LABELS = Map.<String, String>ofEntries(
        Map.entry("address", "Địa chỉ"),
        Map.entry("amount", "Số tiền"),
        Map.entry("areaSqm", "Diện tích (m²)"),
        Map.entry("assetCode", "Mã tài sản"),
        Map.entry("basePrice", "Giá thuê"),
        Map.entry("canCollectPayment", "Được thu tiền"),
        Map.entry("canManageContracts", "Được quản lý hợp đồng"),
        Map.entry("canViewDocuments", "Được xem giấy tờ"),
        Map.entry("capAmount", "Phí tối đa"),
        Map.entry("changedFields", "Trường đã sửa"),
        Map.entry("chargeType", "Cách tính"),
        Map.entry("checkOutDate", "Ngày trả phòng"),
        Map.entry("confidence", "Độ tin cậy OCR"),
        Map.entry("consumption", "Tiêu thụ"),
        Map.entry("contractId", "Mã hợp đồng"),
        Map.entry("cost", "Chi phí"),
        Map.entry("count", "Số lượng"),
        Map.entry("daysOverdue", "Số ngày trễ"),
        Map.entry("deduction", "Khấu trừ"),
        Map.entry("depositAmount", "Tiền cọc"),
        Map.entry("description", "Mô tả"),
        Map.entry("documents", "Số giấy tờ"),
        Map.entry("early", "Chấm dứt không qua check-out"),
        Map.entry("email", "Email"),
        Map.entry("enabled", "Bật"),
        Map.entry("endDate", "Ngày kết thúc"),
        Map.entry("engine", "Bộ đọc OCR"),
        Map.entry("exportId", "Mã lần xuất"),
        Map.entry("feeType", "Kiểu phí"),
        Map.entry("feeValue", "Mức phí"),
        Map.entry("floor", "Tầng"),
        Map.entry("format", "Định dạng"),
        Map.entry("fullName", "Họ tên"),
        Map.entry("graceDays", "Số ngày ân hạn"),
        Map.entry("handoverId", "Mã biên bản"),
        Map.entry("identifierHash", "Mã băm tên đăng nhập"),
        Map.entry("identityCardNumber", "Số CCCD"),
        Map.entry("items", "Số hạng mục"),
        Map.entry("kind", "Loại"),
        Map.entry("legalHold", "Đang giữ dữ liệu"),
        Map.entry("manualEntry", "Nhập tay"),
        Map.entry("match", "Đối chiếu"),
        Map.entry("maxOccupants", "Số người tối đa"),
        Map.entry("meterType", "Đồng hồ"),
        Map.entry("name", "Tên"),
        Map.entry("occupantId", "Mã người ở cùng"),
        Map.entry("ocr", "Dùng OCR"),
        Map.entry("ocrCorrected", "Sửa kết quả OCR"),
        Map.entry("period", "Kỳ"),
        Map.entry("permanentAddress", "Địa chỉ thường trú"),
        Map.entry("phone", "Số điện thoại"),
        Map.entry("photos", "Số ảnh"),
        Map.entry("previousTechnicianId", "Kỹ thuật viên trước"),
        Map.entry("pricePerUnit", "Đơn giá"),
        Map.entry("properties", "Số nhà trọ"),
        Map.entry("propertyId", "Mã nhà trọ"),
        Map.entry("reason", "Lý do"),
        Map.entry("recipients", "Số người được báo"),
        Map.entry("rentAmount", "Tiền thuê"),
        Map.entry("replaced", "Thay đồng hồ mới"),
        Map.entry("requiresMaintenance", "Cần bảo trì"),
        Map.entry("role", "Vai trò"),
        Map.entry("roomId", "Mã phòng"),
        Map.entry("roomNumber", "Số phòng"),
        Map.entry("rows", "Số dòng"),
        Map.entry("serviceName", "Dịch vụ"),
        Map.entry("size", "Dung lượng (byte)"),
        Map.entry("startDate", "Ngày bắt đầu"),
        Map.entry("status", "Trạng thái"),
        Map.entry("targetRole", "Vai trò tài khoản"),
        Map.entry("targetUserId", "Mã tài khoản"),
        Map.entry("technicianId", "Mã kỹ thuật viên"),
        Map.entry("template", "Mẫu"),
        Map.entry("tenantId", "Mã người thuê"),
        Map.entry("totalAmount", "Tổng tiền"),
        Map.entry("totalRooms", "Tổng số phòng"),
        Map.entry("type", "Loại"),
        Map.entry("userId", "Mã người dùng"),
        Map.entry("username", "Tên đăng nhập"),
        Map.entry("valueChanged", "Đã sửa chỉ số"),
        Map.entry("version", "Phiên bản"));

    /** Mã chỉ có một nghĩa trong toàn hệ thống nên dịch được mà không cần biết đối tượng. */
    private static final Map<String, String> VALUE_LABELS = Map.<String, String>ofEntries(
        Map.entry("ACCOUNT_DISABLED", "Tài khoản đã bị khóa"),
        Map.entry("CHECK_IN", "Nhận phòng"),
        Map.entry("CHECK_OUT", "Trả phòng"),
        Map.entry("ELECTRIC", "Điện"),
        Map.entry("ERASURE_REQUEST", "Người thuê yêu cầu xóa"),
        Map.entry("FIXED", "Số tiền cố định"),
        Map.entry("IMAGE", "Ảnh"),
        Map.entry("INVALID_CREDENTIALS", "Sai mật khẩu"),
        Map.entry("MATCH", "Khớp"),
        Map.entry("METER_ELECTRIC", "Ảnh đồng hồ điện"),
        Map.entry("METER_WATER", "Ảnh đồng hồ nước"),
        Map.entry("MISMATCH", "Lệch"),
        Map.entry("NONE", "Chưa có"),
        Map.entry("OWNER_CONFIRMED", "Chủ trọ xác nhận xóa"),
        Map.entry("PERCENT", "Phần trăm"),
        Map.entry("PER_KWH", "Theo số điện"),
        Map.entry("PER_PERSON", "Theo người"),
        Map.entry("PER_ROOM_FLAT", "Theo phòng (cố định)"),
        Map.entry("RETENTION_EXPIRED", "Hết thời hạn lưu trữ"),
        Map.entry("UNKNOWN_USER", "Không có tài khoản này"),
        Map.entry("UNVERIFIED", "Chưa đối chiếu"),
        Map.entry("VIDEO", "Video"),
        Map.entry("WATER", "Nước"),
        Map.entry("XLSX", "Excel"));

    private static final Map<String, String> HANDOVER_STATUS = Map.of(
        "DRAFT", "Bản nháp",
        "PENDING_TENANT", "Chờ người thuê xác nhận",
        "PENDING_APPROVAL", "Chờ duyệt",
        "APPROVED", "Đã duyệt",
        "REJECTED", "Bị từ chối");

    private AuditDetailsFormatter() {}

    static List<Line> format(String targetType, String details) {
        if (details == null || details.isBlank()) {
            return List.of();
        }
        Map<String, Object> map = parse(details);
        if (map == null) {
            return List.of(new Line(null, details));
        }
        List<Line> lines = new ArrayList<>();
        Set<String> done = new HashSet<>();
        if (map.get("before") instanceof Map<?, ?> before && map.get("after") instanceof Map<?, ?> after) {
            changes(targetType, before, after, lines);
            done.add("before");
            done.add("after");
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (!done.add(key)) {
                continue;
            }
            String[] pair = pairOf(key);
            if (pair != null && map.containsKey(pair[0]) && map.containsKey(pair[1])) {
                done.add(pair[0]);
                done.add(pair[1]);
                lines.add(new Line(pairLabel(targetType, pair),
                    value(targetType, pair[0], map.get(pair[0])) + ARROW + value(targetType, pair[1], map.get(pair[1]))));
                continue;
            }
            lines.add(new Line(label(key), value(targetType, key, entry.getValue())));
        }
        return lines;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String details) {
        try {
            return JSON.readValue(details, Object.class) instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
        } catch (JacksonException ex) {
            return null;
        }
    }

    /** Trước/sau (sửa phòng, hợp đồng, chính sách phí...): chỉ hiện trường có giá trị khác nhau. */
    private static void changes(String targetType, Map<?, ?> before, Map<?, ?> after, List<Line> lines) {
        Set<Object> keys = new LinkedHashSet<>(after.keySet());
        keys.addAll(before.keySet());
        int added = 0;
        for (Object rawKey : keys) {
            String key = String.valueOf(rawKey);
            Object oldValue = before.get(rawKey);
            Object newValue = after.get(rawKey);
            if (!sameValue(oldValue, newValue)) {
                lines.add(new Line(label(key), value(targetType, key, oldValue) + ARROW + value(targetType, key, newValue)));
                added++;
            }
        }
        if (added == 0) {
            lines.add(new Line("Thay đổi", "Không có trường nào thay đổi"));
        }
    }

    /** {@code oldX/newX} và {@code from/to} gộp thành một dòng "cũ → mới". */
    private static String[] pairOf(String key) {
        if (key.equals("from") || key.equals("to")) {
            return new String[] {"from", "to"};
        }
        if (key.length() > 3 && key.startsWith("old") && Character.isUpperCase(key.charAt(3))) {
            return new String[] {key, "new" + key.substring(3)};
        }
        if (key.length() > 3 && key.startsWith("new") && Character.isUpperCase(key.charAt(3))) {
            return new String[] {"old" + key.substring(3), key};
        }
        return null;
    }

    private static String pairLabel(String targetType, String[] pair) {
        if (pair[0].equals("from")) {
            return "MAINTENANCE_TICKET".equals(targetType) ? "Bên chịu phí" : "Thay đổi";
        }
        String suffix = pair[1].substring(3);
        return label(Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1));
    }

    private static String label(String key) {
        return KEY_LABELS.getOrDefault(key, key);
    }

    private static String value(String targetType, String key, Object value) {
        if (value == null) {
            return "—";
        }
        if (value instanceof Boolean flag) {
            return flag ? "Có" : "Không";
        }
        if (value instanceof Map<?, ?> nested) {
            return nested.entrySet().stream()
                .map(e -> label(String.valueOf(e.getKey())) + ": " + value(targetType, String.valueOf(e.getKey()), e.getValue()))
                .collect(Collectors.joining("; "));
        }
        if (value instanceof Collection<?> items) {
            Function<Object, String> item = key.equals("changedFields")
                ? field -> label(String.valueOf(field))
                : element -> value(targetType, key, element);
            return items.stream().map(item).collect(Collectors.joining(", "));
        }
        String text = String.valueOf(value);
        if (MONEY_KEYS.contains(key)) {
            return money(text);
        }
        if (STATUS_KEYS.contains(key)) {
            return status(targetType, text);
        }
        if (key.equals("role") || key.equals("targetRole")) {
            UserRole role = parseEnum(UserRole.class, text);
            return role == null ? text : TEXT.role(role);
        }
        if (key.equals("from") || key.equals("to")) {
            LiableParty party = parseEnum(LiableParty.class, text);
            return party == null ? text : TEXT.liableParty(party);
        }
        if (key.equals("type") && "ROOM_ASSET".equals(targetType)) {
            AssetEventType type = parseEnum(AssetEventType.class, text);
            return type == null ? text : TEXT.assetEventType(type);
        }
        return VALUE_LABELS.getOrDefault(text, text);
    }

    /** Trạng thái phụ thuộc đối tượng: cùng mã (REJECTED, ACTIVE...) có nghĩa khác nhau ở mỗi loại. */
    private static String status(String targetType, String code) {
        String label = switch (targetType == null ? "" : targetType) {
            case "ROOM" -> translate(RoomStatus.class, code, TEXT::roomStatus);
            case "CONTRACT" -> translate(ContractStatus.class, code, TEXT::contractStatus);
            case "MAINTENANCE_TICKET" -> translate(MaintenanceStatus.class, code, TEXT::maintenanceStatus);
            case "PROPERTY" -> translate(PropertyApprovalStatus.class, code, TEXT::approvalStatus);
            case "ROOM_ASSET" -> translate(AssetStatus.class, code, TEXT::assetStatus);
            case "USER" -> translate(User.UserStatus.class, code, TEXT::userStatus);
            case "HANDOVER" -> HANDOVER_STATUS.get(code);
            default -> null;
        };
        return label != null ? label : code;
    }

    private static <E extends Enum<E>> String translate(Class<E> type, String code, Function<E, String> labeler) {
        E parsed = parseEnum(type, code);
        return parsed == null ? null : labeler.apply(parsed);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String code) {
        try {
            return Enum.valueOf(type, code);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Số tiền "3500000.00" → "3,500,000 đ" như các trang khác; giá trị đã che ("***") giữ nguyên. */
    private static String money(String text) {
        BigDecimal amount = number(text);
        if (amount == null) {
            return text;
        }
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US)).format(amount) + " đ";
    }

    private static boolean sameValue(Object a, Object b) {
        if (Objects.equals(a, b)) {
            return true;
        }
        BigDecimal x = a == null ? null : number(String.valueOf(a));
        BigDecimal y = b == null ? null : number(String.valueOf(b));
        return x != null && y != null && x.compareTo(y) == 0;
    }

    private static BigDecimal number(String text) {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
