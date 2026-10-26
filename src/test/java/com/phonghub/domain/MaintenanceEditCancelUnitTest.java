package com.phonghub.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.domain.exception.InvalidMaintenanceStateException;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.MaintenanceTicketEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #89: sửa/hủy phiếu bảo trì chưa xử lý ở tầng domain. */
class MaintenanceEditCancelUnitTest {

    private static MaintenanceTicket reported() {
        MaintenanceTicket ticket = MaintenanceTicket.create(UUID.randomUUID(), UUID.randomUUID(), null,
            "Vòi nước rỉ", "Rỉ ở bồn rửa", MaintenancePriority.LOW, null, Duration.ofHours(72));
        ticket.drainEvents();
        return ticket;
    }

    @Test
    @DisplayName("Sửa nội dung: đổi mức độ tính lại hạn từ lúc tạo; giữ mức độ thì giữ hạn")
    void updateRecomputesDueAtOnlyWhenPriorityChanges() {
        MaintenanceTicket ticket = reported();
        Instant due = ticket.getDueAt();
        ticket.markSlaNotified(Instant.now());

        ticket.updateRequest(" Vòi nước rỉ mạnh ", " Rỉ cả đêm ", MaintenancePriority.LOW, Duration.ofHours(4));
        assertEquals("Vòi nước rỉ mạnh", ticket.getTitle());
        assertEquals("Rỉ cả đêm", ticket.getDescription());
        assertEquals(due, ticket.getDueAt(), "cùng mức độ thì không đổi hạn");

        ticket.updateRequest("Vòi nước rỉ mạnh", "Rỉ cả đêm", MaintenancePriority.URGENT, Duration.ofHours(4));
        assertEquals(MaintenancePriority.URGENT, ticket.getPriority());
        assertEquals(ticket.getCreatedAt().plus(Duration.ofHours(4)), ticket.getDueAt());
        assertNull(ticket.getSlaNotifiedAt(), "hạn mới thì được cảnh báo quá hạn lại");
        assertEquals(MaintenanceStatus.REPORTED, ticket.getStatus());
        assertTrue(ticket.drainEvents().isEmpty(), "sửa nội dung không phải mốc trạng thái");
    }

    @Test
    @DisplayName("Thiếu tiêu đề/mô tả thì từ chối; phiếu đã giao vẫn sửa được, đang xử lý thì không")
    void updateValidationAndStates() {
        MaintenanceTicket ticket = reported();
        assertThrows(IllegalArgumentException.class,
            () -> ticket.updateRequest(" ", "mô tả", MaintenancePriority.LOW, null));
        assertThrows(IllegalArgumentException.class,
            () -> ticket.updateRequest("tiêu đề", "", MaintenancePriority.LOW, null));

        ticket.assign(UUID.randomUUID());
        assertTrue(ticket.isEditable());
        ticket.updateRequest("Đã giao vẫn sửa", "mô tả", MaintenancePriority.MEDIUM, null);
        assertNull(ticket.getDueAt(), "SLA null là không đặt hạn");

        ticket.startWork();
        assertFalse(ticket.isEditable());
        assertThrows(InvalidMaintenanceStateException.class,
            () -> ticket.updateRequest("x", "y", MaintenancePriority.LOW, null));
        assertThrows(InvalidMaintenanceStateException.class, () -> ticket.cancel("muộn"));
    }

    @Test
    @DisplayName("Hủy: chuyển CANCELLED, ghi mốc kèm lý do, coi như đã đóng nên không quá hạn; không hủy lại được")
    void cancel() {
        MaintenanceTicket ticket = reported();
        ticket.cancel("  Trùng phiếu  ");

        assertEquals(MaintenanceStatus.CANCELLED, ticket.getStatus());
        assertTrue(ticket.isWorkFinished());
        assertFalse(ticket.isOverdue(Instant.now().plus(Duration.ofDays(30))));
        List<MaintenanceTicketEvent> events = ticket.drainEvents();
        assertEquals(1, events.size());
        assertEquals(MaintenanceStatus.REPORTED, events.getFirst().fromStatus());
        assertEquals(MaintenanceStatus.CANCELLED, events.getFirst().toStatus());
        assertEquals("Hủy phiếu: Trùng phiếu", events.getFirst().note());

        assertThrows(InvalidMaintenanceStateException.class, () -> ticket.cancel(null));
        MaintenanceTicket noReason = reported();
        noReason.cancel(null);
        assertEquals("Hủy phiếu", noReason.drainEvents().getFirst().note());
    }
}
