package com.phonghub.domain.model;

import com.phonghub.domain.exception.MaintenanceTicketException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class MaintenanceTicket {
    private final UUID id;
    private final UUID roomId;
    private final UUID propertyId;
    private final UUID requestedByTenantId;
    private UUID assignedTechnicianId;
    private final String title;
    private final String description;
    private MaintenancePriority priority;
    private MaintenanceStatus status;
    private BigDecimal repairCost;
    private String resolutionNotes;
    private final Instant createdAt;
    private Instant updatedAt;

    public MaintenanceTicket(
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
        if (id == null) {
            throw new IllegalArgumentException("Ticket id cannot be null");
        }
        if (roomId == null) {
            throw new IllegalArgumentException("Room id cannot be null");
        }
        if (propertyId == null) {
            throw new IllegalArgumentException("Property id cannot be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title cannot be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description cannot be blank");
        }

        this.id = id;
        this.roomId = roomId;
        this.propertyId = propertyId;
        this.requestedByTenantId = requestedByTenantId;
        this.assignedTechnicianId = assignedTechnicianId;
        this.title = title.trim();
        this.description = description.trim();
        this.priority = priority != null ? priority : MaintenancePriority.MEDIUM;
        this.status = status != null ? status : MaintenanceStatus.REPORTED;
        this.repairCost = repairCost != null ? repairCost : BigDecimal.ZERO;
        this.resolutionNotes = resolutionNotes;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public static MaintenanceTicket create(
        UUID roomId,
        UUID propertyId,
        UUID requestedByTenantId,
        String title,
        String description,
        MaintenancePriority priority
    ) {
        Instant now = Instant.now();
        return new MaintenanceTicket(
            UUID.randomUUID(),
            roomId,
            propertyId,
            requestedByTenantId,
            null,
            title,
            description,
            priority,
            MaintenanceStatus.REPORTED,
            BigDecimal.ZERO,
            null,
            now,
            now
        );
    }

    public void assignTo(UUID technicianId) {
        if (technicianId == null) {
            throw new IllegalArgumentException("Technician id cannot be null");
        }
        if (this.status == MaintenanceStatus.RESOLVED || this.status == MaintenanceStatus.REJECTED) {
            throw new MaintenanceTicketException("Cannot assign technician to a ticket that is already " + status);
        }
        this.assignedTechnicianId = technicianId;
        this.status = MaintenanceStatus.ASSIGNED;
        this.updatedAt = Instant.now();
    }

    public void accept(UUID technicianId) {
        assignTo(technicianId);
        this.status = MaintenanceStatus.IN_PROGRESS;
        this.updatedAt = Instant.now();
    }

    public void startWork() {
        if (this.status != MaintenanceStatus.ASSIGNED) {
            throw new MaintenanceTicketException("Ticket must be ASSIGNED before starting work");
        }
        this.status = MaintenanceStatus.IN_PROGRESS;
        this.updatedAt = Instant.now();
    }

    public void resolve(String notes, BigDecimal cost) {
        if (this.status != MaintenanceStatus.ASSIGNED && this.status != MaintenanceStatus.IN_PROGRESS) {
            throw new MaintenanceTicketException("Ticket cannot be resolved from status " + status + " (must be ASSIGNED or IN_PROGRESS)");
        }
        if (notes == null || notes.isBlank()) {
            throw new MaintenanceTicketException("Resolution notes are required to resolve a ticket");
        }
        this.resolutionNotes = notes.trim();
        if (cost != null) {
            if (cost.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Repair cost cannot be negative");
            }
            this.repairCost = cost;
        }
        this.status = MaintenanceStatus.RESOLVED;
        this.updatedAt = Instant.now();
    }

    public boolean isResolved() {
        return this.status == MaintenanceStatus.RESOLVED || this.status == MaintenanceStatus.VERIFIED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getRequestedByTenantId() {
        return requestedByTenantId;
    }

    public UUID getAssignedTechnicianId() {
        return assignedTechnicianId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public MaintenancePriority getPriority() {
        return priority;
    }

    public MaintenanceStatus getStatus() {
        return status;
    }

    public BigDecimal getRepairCost() {
        return repairCost;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MaintenanceTicket ticket = (MaintenanceTicket) o;
        return Objects.equals(id, ticket.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
