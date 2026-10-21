package com.phonghub.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.phonghub.adapter.in.web.AuditDetailsFormatter.Line;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #78: cột Chi tiết của Nhật ký hiện tiếng Việt thay vì JSON thô. */
class AuditDetailsFormatterTest {

    @Test
    @DisplayName("trước/sau chỉ hiện trường thay đổi, so số theo giá trị, tiền có phân cách")
    void beforeAfterShowsChangedFieldsOnly() {
        String json = """
            {"after":{"roomNumber":"P101","floor":1,"areaSqm":"25.00","basePrice":"3600000","maxOccupants":2},
             "before":{"roomNumber":"P101","floor":1,"areaSqm":"25","basePrice":"3500000.00","maxOccupants":2}}""";

        assertEquals(List.of(new Line("Giá thuê", "3,500,000 đ → 3,600,000 đ")),
            AuditDetailsFormatter.format("ROOM", json));
        assertEquals(List.of(new Line("Thay đổi", "Không có trường nào thay đổi")),
            AuditDetailsFormatter.format("ROOM", "{\"before\":{\"basePrice\":\"3500000.00\"},\"after\":{\"basePrice\":\"3500000\"}}"));
    }

    @Test
    @DisplayName("cặp cũ/mới gộp một dòng; trạng thái dịch theo loại đối tượng")
    void statusPairsTranslatedByTarget() {
        assertEquals(List.of(new Line("Trạng thái", "Bảo trì → Trống")),
            AuditDetailsFormatter.format("ROOM", "{\"newStatus\":\"AVAILABLE\",\"oldStatus\":\"MAINTENANCE\"}"));
        assertEquals(List.of(new Line("Trạng thái", "Bị từ chối")),
            AuditDetailsFormatter.format("PROPERTY", "{\"status\":\"REJECTED\"}"));
        assertEquals(List.of(new Line("Trạng thái", "Đã từ chối")),
            AuditDetailsFormatter.format("MAINTENANCE_TICKET", "{\"status\":\"REJECTED\"}"));
        assertEquals(List.of(new Line("Bên chịu phí", "Chủ trọ → Người thuê")),
            AuditDetailsFormatter.format("MAINTENANCE_TICKET", "{\"from\":\"OWNER\",\"to\":\"TENANT\"}"));
    }

    @Test
    @DisplayName("khóa và mã có nhãn; danh sách trường đã sửa; giá trị đã che giữ nguyên; mã lạ giữ nguyên")
    void labelsAndFallbacks() {
        assertEquals(List.of(new Line("Lý do", "Sai mật khẩu"), new Line("Tên đăng nhập", "owner1")),
            AuditDetailsFormatter.format("USER", "{\"reason\":\"INVALID_CREDENTIALS\",\"username\":\"owner1\"}"));
        assertEquals(List.of(new Line("Trường đã sửa", "Tên, Địa chỉ"), new Line("Vai trò", "Chủ trọ")),
            AuditDetailsFormatter.format("PROPERTY", "{\"changedFields\":[\"name\",\"address\"],\"role\":\"OWNER\"}"));
        assertEquals(List.of(new Line("Tổng tiền", "***"), new Line("Được thu tiền", "Có")),
            AuditDetailsFormatter.format("INVOICE", "{\"totalAmount\":\"***\",\"canCollectPayment\":true}"));
        assertEquals(List.of(new Line("newKey", "SOME_CODE")),
            AuditDetailsFormatter.format("ROOM", "{\"newKey\":\"SOME_CODE\"}"));
    }

    @Test
    @DisplayName("chi tiết không phải JSON hiện nguyên văn, rỗng thì không có dòng nào")
    void nonJsonShownAsIs() {
        assertEquals(List.of(new Line(null, "[không đọc được chi tiết]")),
            AuditDetailsFormatter.format("ROOM", "[không đọc được chi tiết]"));
        assertEquals(List.of(new Line(null, "{roomNumber=P101}")),
            AuditDetailsFormatter.format("ROOM", "{roomNumber=P101}"));
        assertEquals(List.of(), AuditDetailsFormatter.format("ROOM", null));
        assertEquals(List.of(), AuditDetailsFormatter.format("ROOM", " "));
    }
}
