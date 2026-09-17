package com.phonghub.adapter.in.web.api.dto;

import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MaintenanceTicketResponse(
    UUID id,
    UUID roomId,
    UUID propertyId,
    UUID requestedByTenantId,
    UUID assignedTechnicianId,
    String title,
    String description,
    MaintenancePriority priority,
    MaintenanceStatus status,
    BigDecimal repairCost,
    String resolutionNotes,
    Instant createdAt,
    Instant updatedAt
) {
    public static MaintenanceTicketResponse from(MaintenanceTicket ticket) {
        return new MaintenanceTicketResponse(
            ticket.getId(),
            ticket.getRoomId(),
            ticket.getPropertyId(),
            ticket.getRequestedByTenantId(),
            ticket.getAssignedTechnicianId(),
            ticket.getTitle(),
            ticket.getDescription(),
            ticket.getPriority(),
            ticket.getStatus(),
            ticket.getRepairCost(),
            ticket.getResolutionNotes(),
            ticket.getCreatedAt(),
            ticket.getUpdatedAt()
        );
    }
}
