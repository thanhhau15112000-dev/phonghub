package com.phonghub.adapter.in.web.api.dto;

import com.phonghub.application.service.MaintenanceAssignmentTimeoutPolicy;
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
    String rejectionReason,
    Instant dueAt,
    boolean overdue,
    Instant createdAt,
    Instant updatedAt,
    Instant assignedAt,
    Instant understaffedNotifiedAt,
    Instant autoCancelAt
) {
    public static MaintenanceTicketResponse from(MaintenanceTicket ticket) {
        return from(ticket, MaintenanceAssignmentTimeoutPolicy.defaults());
    }

    public static MaintenanceTicketResponse from(
        MaintenanceTicket ticket,
        MaintenanceAssignmentTimeoutPolicy timeoutPolicy
    ) {
        Instant notifiedAt = ticket.getUnderstaffedNotifiedAt();
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
            ticket.getRejectionReason(),
            ticket.getDueAt(),
            ticket.isOverdue(Instant.now()),
            ticket.getCreatedAt(),
            ticket.getUpdatedAt(),
            ticket.getAssignedAt(),
            notifiedAt,
            notifiedAt == null ? null : notifiedAt.plus(timeoutPolicy.understaffedCancelAfter())
        );
    }
}
