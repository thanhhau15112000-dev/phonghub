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
    private final MaintenanceCause causeCategory;
    private LiableParty liableParty;
    private final Instant createdAt;
    private Instant updatedAt;

    /** Phiếu không có thông tin nguyên nhân: chủ trọ chịu phí. */
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
        this(id, roomId, propertyId, requestedByTenantId, assignedTechnicianId, title, description,
            priority, status, repairCost, resolutionNotes, null, LiableParty.OWNER, createdAt, updatedAt);
    }

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
        MaintenanceCause causeCategory,
        LiableParty liableParty,
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
        this.causeCategory = causeCategory;
        this.liableParty = liableParty != null ? liableParty : LiableParty.OWNER;
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
        return create(roomId, propertyId, requestedByTenantId, title, description, priority, null);
    }

    /** Bên chịu phí ban đầu suy ra từ nguyên nhân khai; không có nguyên nhân thì chủ trọ chịu. */
    public static MaintenanceTicket create(
        UUID roomId,
        UUID propertyId,
        UUID requestedByTenantId,
        String title,
        String description,
        MaintenancePriority priority,
        MaintenanceCause causeCategory
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
            causeCategory,
            causeCategory != null ? causeCategory.defaultLiableParty() : LiableParty.OWNER,
            now,
            now
        );
    }

    public void assignTo(UUID technicianId) {
        if (technicianId == null) {
            throw new IllegalArgumentException("Technician id cannot be null");
        }
        if (this.status == MaintenanceStatus.RESOLVED || this.status == MaintenanceStatus.REJECTED
            || this.status == MaintenanceStatus.AWAITING_PAYMENT) {
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
        if (this.repairCost.signum() > 0 && this.liableParty == LiableParty.UNDETERMINED) {
            throw new MaintenanceTicketException("Cần xác định bên chịu phí trước khi ghi nhận chi phí sửa chữa");
        }
        this.status = chargesTenant() ? MaintenanceStatus.AWAITING_PAYMENT : MaintenanceStatus.RESOLVED;
        this.updatedAt = Instant.now();
    }

    /** Người thuê chịu phí và có chi phí > 0: phải thu tiền qua hóa đơn. */
    public boolean chargesTenant() {
        return this.liableParty == LiableParty.TENANT && this.repairCost.signum() > 0;
    }

    /** Đổi bên chịu phí khi chưa sửa xong (kỹ thuật viên / quản lý xác nhận lại lời khai). */
    public void changeLiableParty(LiableParty party) {
        if (party == null) {
            throw new IllegalArgumentException("Liable party cannot be null");
        }
        if (isWorkFinished()) {
            throw new MaintenanceTicketException("Không thể đổi bên chịu phí khi phiếu đã xử lý xong: " + status);
        }
        this.liableParty = party;
        this.updatedAt = Instant.now();
    }

    /** Hóa đơn phí sửa chữa đã thanh toán đủ. */
    public void markFeePaid() {
        if (this.status != MaintenanceStatus.AWAITING_PAYMENT) {
            throw new MaintenanceTicketException("Ticket is not awaiting payment: " + status);
        }
        this.status = MaintenanceStatus.RESOLVED;
        this.updatedAt = Instant.now();
    }

    /** Quản lý miễn khoản phí: chủ trọ chịu, phiếu hoàn tất. */
    public void waiveFee() {
        if (this.status != MaintenanceStatus.AWAITING_PAYMENT) {
            throw new MaintenanceTicketException("Chỉ có thể miễn phí cho phiếu đang chờ thanh toán: " + status);
        }
        this.liableParty = LiableParty.OWNER;
        this.status = MaintenanceStatus.RESOLVED;
        this.updatedAt = Instant.now();
    }

    public boolean isResolved() {
        return this.status == MaintenanceStatus.RESOLVED || this.status == MaintenanceStatus.VERIFIED;
    }

    /** Việc sửa đã xong (hoặc phiếu đã đóng): không còn chặn việc nhả phòng khỏi bảo trì. */
    public boolean isWorkFinished() {
        return isResolved()
            || this.status == MaintenanceStatus.AWAITING_PAYMENT
            || this.status == MaintenanceStatus.REJECTED;
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

    public MaintenanceCause getCauseCategory() {
        return causeCategory;
    }

    public LiableParty getLiableParty() {
        return liableParty;
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
