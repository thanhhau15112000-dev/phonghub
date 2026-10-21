package com.phonghub.application.port.in;

import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceCause;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.MaintenanceTicketEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface MaintenanceUseCase {
    MaintenanceTicket createTicket(CreateMaintenanceTicketCommand command);
    MaintenanceTicket acceptTicket(UUID ticketId);
    /** Owner/staff giao phiếu cho kỹ thuật viên đã được phân công vào nhà trọ của phiếu. */
    MaintenanceTicket assignTechnician(UUID ticketId, UUID technicianId);
    /** Owner/staff từ chối phiếu chưa xử lý; bắt buộc có lý do, người thuê được thông báo. */
    MaintenanceTicket rejectTicket(UUID ticketId, String reason);

    /**
     * Sửa tiêu đề, mô tả, mức ưu tiên của phiếu chưa xử lý (#89): người thuê đã báo phiếu, hoặc người quản lý nhà trọ.
     * Phiếu đã bắt đầu xử lý thì 409.
     */
    MaintenanceTicket updateTicket(UpdateMaintenanceTicketCommand command);

    /** Hủy phiếu chưa xử lý (#89), cùng quyền như sửa; chuyển CANCELLED, không xóa. */
    MaintenanceTicket cancelTicket(UUID ticketId, String reason);

    record UpdateMaintenanceTicketCommand(UUID ticketId, String title, String description,
                                          com.phonghub.domain.model.MaintenancePriority priority) {
        public UpdateMaintenanceTicketCommand {
            if (ticketId == null) {
                throw new IllegalArgumentException("Ticket id is required");
            }
        }
    }
    MaintenanceTicket resolveTicket(ResolveMaintenanceTicketCommand command);
    /** Quản lý miễn khoản phí đang chờ thanh toán: hủy hóa đơn, chủ trọ chịu phí, phiếu hoàn tất. */
    MaintenanceTicket waiveRepairFee(UUID ticketId, String reason);
    MaintenanceTicket getTicket(UUID ticketId);
    /**
     * Báo giá (#83): kỹ thuật viên được giao phiếu, owner/staff quản lý nhà trọ hoặc admin; chỉ khi phiếu chưa sửa
     * xong (409 nếu đã xong/đã từ chối).
     */
    MaintenanceTicket quoteTicket(UUID ticketId, BigDecimal amount, String note);
    /**
     * Báo giá người xem được thấy: người quản lý và kỹ thuật viên luôn thấy; người thuê chỉ thấy khi mình là bên chịu
     * phí. Trả null nếu chưa báo giá hoặc bị ẩn; 403 nếu không được xem phiếu.
     */
    MaintenanceTicket.Quote visibleQuote(UUID ticketId);
    /** Người thuê của phòng xác nhận đã sửa đạt: RESOLVED → VERIFIED (409 nếu chưa sửa xong). */
    MaintenanceTicket verifyTicket(UUID ticketId);
    /** Người thuê của phòng phản hồi chưa đạt kèm lý do: phiếu mở lại để sửa tiếp (409 nếu chưa sửa xong). */
    MaintenanceTicket reopenTicket(UUID ticketId, String feedback);
    /** Timeline trạng thái của phiếu; cùng quy tắc xem với {@link #getTicket}. */
    List<MaintenanceTicketEvent> getTimeline(UUID ticketId);
    List<MaintenanceTicket> listTicketsForProperty(UUID propertyId);
    List<MaintenanceTicket> listAssignedTicketsForTechnician(UUID technicianId);
    List<MaintenanceTicket> listTicketsForRoom(UUID roomId);

    record CreateMaintenanceTicketCommand(
        UUID roomId,
        String title,
        String description,
        MaintenancePriority priority,
        boolean setRoomMaintenance,
        MaintenanceCause cause
    ) {
        public CreateMaintenanceTicketCommand(
            UUID roomId, String title, String description, MaintenancePriority priority, boolean setRoomMaintenance
        ) {
            this(roomId, title, description, priority, setRoomMaintenance, null);
        }

        public CreateMaintenanceTicketCommand {
            if (roomId == null) {
                throw new IllegalArgumentException("Room id cannot be null");
            }
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("Title cannot be blank");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("Description cannot be blank");
            }
            if (priority == null) {
                priority = MaintenancePriority.MEDIUM;
            }
        }
    }

    record ResolveMaintenanceTicketCommand(
        UUID ticketId,
        String resolutionNotes,
        BigDecimal repairCost,
        boolean releaseRoomToAvailable,
        LiableParty liableParty
    ) {
        public ResolveMaintenanceTicketCommand(
            UUID ticketId, String resolutionNotes, BigDecimal repairCost, boolean releaseRoomToAvailable
        ) {
            this(ticketId, resolutionNotes, repairCost, releaseRoomToAvailable, null);
        }

        public ResolveMaintenanceTicketCommand {
            if (ticketId == null) {
                throw new IllegalArgumentException("Ticket id cannot be null");
            }
            if (resolutionNotes == null || resolutionNotes.isBlank()) {
                throw new IllegalArgumentException("Resolution notes cannot be blank");
            }
            if (repairCost != null && repairCost.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Repair cost cannot be negative");
            }
        }
    }
}
