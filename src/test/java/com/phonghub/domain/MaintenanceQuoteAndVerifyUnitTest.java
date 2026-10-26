package com.phonghub.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.domain.exception.InvalidMaintenanceStateException;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.MaintenanceTicketEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** #83: báo giá, người thuê xác nhận hoặc phản hồi chưa đạt. */
class MaintenanceQuoteAndVerifyUnitTest {

    private static MaintenanceTicket ticket(MaintenanceStatus status, UUID technicianId) {
        return new MaintenanceTicket(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
            technicianId, "Vòi nước rỉ", "Bồn rửa", MaintenancePriority.MEDIUM, status, BigDecimal.ZERO, null, null,
            LiableParty.OWNER, null, null, null, Instant.now(), Instant.now());
    }

    @ParameterizedTest
    @EnumSource(value = MaintenanceStatus.class, names = {"REPORTED", "ASSIGNED", "IN_PROGRESS"})
    @DisplayName("báo giá được khi chưa sửa xong, không đổi trạng thái, ghi timeline")
    void quoteWhileOpen(MaintenanceStatus status) {
        MaintenanceTicket t = ticket(status, UUID.randomUUID());
        UUID actor = UUID.randomUUID();

        t.quote(new BigDecimal("500000"), "  Thay vòi  ", actor);

        assertEquals(status, t.getStatus());
        assertEquals(new BigDecimal("500000"), t.getQuote().amount());
        assertEquals("Thay vòi", t.getQuote().note());
        assertEquals(actor, t.getQuote().quotedBy());
        List<MaintenanceTicketEvent> events = t.drainEvents();
        assertEquals(1, events.size());
        assertEquals("Báo giá 500.000 đ: Thay vòi", events.get(0).note());

        t.quote(new BigDecimal("650000"), " ", actor);
        assertEquals(new BigDecimal("650000"), t.getQuote().amount(), "cập nhật báo giá");
        assertNull(t.getQuote().note());
    }

    @ParameterizedTest
    @EnumSource(value = MaintenanceStatus.class, names = {"AWAITING_PAYMENT", "RESOLVED", "VERIFIED", "REJECTED"})
    @DisplayName("không báo giá khi phiếu đã xong hoặc bị từ chối (409)")
    void quoteRejectedWhenFinished(MaintenanceStatus status) {
        MaintenanceTicket t = ticket(status, null);
        assertThrows(InvalidMaintenanceStateException.class, () -> t.quote(BigDecimal.ONE, null, UUID.randomUUID()));
        assertNull(t.getQuote());
    }

    @Test
    @DisplayName("số tiền báo giá bắt buộc, không âm; ghi chú tối đa 1000 ký tự")
    void quoteValidation() {
        MaintenanceTicket t = ticket(MaintenanceStatus.IN_PROGRESS, null);
        assertThrows(IllegalArgumentException.class, () -> t.quote(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> t.quote(new BigDecimal("-1"), null, null));
        assertThrows(IllegalArgumentException.class, () -> t.quote(BigDecimal.TEN, "x".repeat(1001), null));
        t.quote(BigDecimal.ZERO, null, null);
        assertEquals(BigDecimal.ZERO, t.getQuote().amount(), "báo giá 0 đồng (sửa miễn phí) hợp lệ");
    }

    @Test
    @DisplayName("người thuê xác nhận: RESOLVED → VERIFIED; trạng thái khác bị 409")
    void verify() {
        MaintenanceTicket t = ticket(MaintenanceStatus.RESOLVED, null);
        t.verifyByTenant();
        assertEquals(MaintenanceStatus.VERIFIED, t.getStatus());
        assertTrue(t.isResolved());
        assertEquals(MaintenanceStatus.RESOLVED, t.drainEvents().get(0).fromStatus());

        assertThrows(InvalidMaintenanceStateException.class, t::verifyByTenant, "xác nhận hai lần");
        for (MaintenanceStatus status : List.of(MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.AWAITING_PAYMENT,
            MaintenanceStatus.REJECTED)) {
            assertThrows(InvalidMaintenanceStateException.class, () -> ticket(status, null).verifyByTenant(), status.name());
        }
    }

    @Test
    @DisplayName("phản hồi chưa đạt: về ASSIGNED nếu có kỹ thuật viên, ngược lại REPORTED; bắt buộc lý do")
    void reopen() {
        MaintenanceTicket withTech = ticket(MaintenanceStatus.RESOLVED, UUID.randomUUID());
        withTech.reopenByTenant("  vẫn rỉ nước ");
        assertEquals(MaintenanceStatus.ASSIGNED, withTech.getStatus());
        assertEquals("Người thuê phản hồi chưa đạt: vẫn rỉ nước", withTech.drainEvents().get(0).note());

        MaintenanceTicket noTech = ticket(MaintenanceStatus.RESOLVED, null);
        noTech.reopenByTenant("chưa sửa");
        assertEquals(MaintenanceStatus.REPORTED, noTech.getStatus());

        MaintenanceTicket resolved = ticket(MaintenanceStatus.RESOLVED, null);
        assertThrows(IllegalArgumentException.class, () -> resolved.reopenByTenant(" "));
        assertThrows(IllegalArgumentException.class, () -> resolved.reopenByTenant("x".repeat(1001)));
        assertEquals(MaintenanceStatus.RESOLVED, resolved.getStatus());
        assertThrows(InvalidMaintenanceStateException.class,
            () -> ticket(MaintenanceStatus.VERIFIED, null).reopenByTenant("muộn"));
        assertThrows(InvalidMaintenanceStateException.class,
            () -> ticket(MaintenanceStatus.AWAITING_PAYMENT, null).reopenByTenant("chưa trả"));
    }

    @Test
    @DisplayName("phiếu mở lại có thể xử lý xong lần nữa")
    void reopenedTicketCanBeResolvedAgain() {
        MaintenanceTicket t = ticket(MaintenanceStatus.RESOLVED, UUID.randomUUID());
        t.reopenByTenant("còn rỉ");
        t.resolve("Thay gioăng", null);
        assertEquals(MaintenanceStatus.RESOLVED, t.getStatus());
    }
}
