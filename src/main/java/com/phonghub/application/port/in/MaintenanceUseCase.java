package com.phonghub.application.port.in;

import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceCause;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceTicket;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface MaintenanceUseCase {
    MaintenanceTicket createTicket(CreateMaintenanceTicketCommand command);
    MaintenanceTicket acceptTicket(UUID ticketId);
    MaintenanceTicket resolveTicket(ResolveMaintenanceTicketCommand command);
    /** Quản lý miễn khoản phí đang chờ thanh toán: hủy hóa đơn, chủ trọ chịu phí, phiếu hoàn tất. */
    MaintenanceTicket waiveRepairFee(UUID ticketId, String reason);
    MaintenanceTicket getTicket(UUID ticketId);
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
