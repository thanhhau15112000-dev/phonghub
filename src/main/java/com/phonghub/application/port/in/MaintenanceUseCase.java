package com.phonghub.application.port.in;

import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceTicket;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface MaintenanceUseCase {
    MaintenanceTicket createTicket(CreateMaintenanceTicketCommand command);
    MaintenanceTicket acceptTicket(UUID ticketId);
    MaintenanceTicket resolveTicket(ResolveMaintenanceTicketCommand command);
    MaintenanceTicket getTicket(UUID ticketId);
    List<MaintenanceTicket> listTicketsForProperty(UUID propertyId);
    List<MaintenanceTicket> listAssignedTicketsForTechnician(UUID technicianId);
    List<MaintenanceTicket> listTicketsForRoom(UUID roomId);

    record CreateMaintenanceTicketCommand(
        UUID roomId,
        String title,
        String description,
        MaintenancePriority priority,
        boolean setRoomMaintenance
    ) {
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
        boolean releaseRoomToAvailable
    ) {
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
