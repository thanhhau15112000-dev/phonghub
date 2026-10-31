package com.phonghub.domain.model;

import com.phonghub.domain.exception.InvalidMaintenanceStateException;
import com.phonghub.domain.exception.MaintenanceTicketException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MaintenanceTicket {
    private final UUID id;
    private final UUID roomId;
    private final UUID propertyId;
    private final UUID requestedByTenantId;
    private UUID assignedTechnicianId;
    private String title;
    private String description;
    private MaintenancePriority priority;
    private MaintenanceStatus status;
    private BigDecimal repairCost;
    private String resolutionNotes;
    private final MaintenanceCause causeCategory;
    private LiableParty liableParty;
    private String rejectionReason;
    private Instant dueAt;
    private Instant slaNotifiedAt;
    private Instant assignedAt;
    private Instant understaffedNotifiedAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;
    /** Mốc chuyển trạng thái chưa lưu; repository ghi vào timeline khi lưu phiếu rồi xóa. */
    private final List<MaintenanceTicketEvent> pendingEvents = new ArrayList<>();
    /** Báo giá sửa chữa gần nhất (#83); null khi chưa báo giá. */
    private Quote quote;
    private Instant branchConfirmedAt;
    private UUID branchConfirmedBy;
    private Instant approvalDueAt;
    private Instant ownerApprovedAt;
    private Instant approvalEscalatedAt;
    private Instant quoteDueAt;
    private Instant quoteApprovedAt;
    private BigDecimal approvedQuoteTotal;
    private int negotiationRounds;
    private int quoteVersion;
    /** Trạng thái quay lại sau khi hết chờ duyệt (REPORTED/ASSIGNED/IN_PROGRESS). */
    private MaintenanceStatus resumeStatus;
    private Instant reminderSentAt;
    private boolean requoteRequested;

    /** Số vòng thương lượng báo giá tối đa mỗi đợt (#203). */
    public static final int MAX_NEGOTIATION_ROUNDS = 2;

    /** Trạng thái chốt nhánh và duyệt trước khi sửa (#202, #203); tầng lưu trữ ghi và đọc nguyên khối. */
    public record ApprovalState(
        Instant branchConfirmedAt,
        UUID branchConfirmedBy,
        Instant approvalDueAt,
        Instant ownerApprovedAt,
        Instant approvalEscalatedAt,
        Instant quoteDueAt,
        Instant quoteApprovedAt,
        BigDecimal approvedQuoteTotal,
        int negotiationRounds,
        int quoteVersion,
        MaintenanceStatus resumeStatus,
        Instant reminderSentAt,
        boolean requoteRequested
    ) {}

    /** Báo giá do kỹ thuật viên hoặc người quản lý nhập trước khi sửa. */
    public record Quote(BigDecimal amount, String note, UUID quotedBy, Instant quotedAt) {}

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
        this(id, roomId, propertyId, requestedByTenantId, assignedTechnicianId, title, description,
            priority, status, repairCost, resolutionNotes, causeCategory, liableParty, null, createdAt, updatedAt);
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
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, roomId, propertyId, requestedByTenantId, assignedTechnicianId, title, description,
            priority, status, repairCost, resolutionNotes, causeCategory, liableParty, rejectionReason, null, null,
            createdAt, updatedAt);
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
        String rejectionReason,
        Instant dueAt,
        Instant slaNotifiedAt,
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
            throw new IllegalArgumentException("Tiêu đề không được để trống.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Mô tả không được để trống.");
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
        this.rejectionReason = rejectionReason;
        this.dueAt = dueAt;
        this.slaNotifiedAt = slaNotifiedAt;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.assignedAt = assignedTechnicianId != null
            && (this.status == MaintenanceStatus.ASSIGNED || this.status == MaintenanceStatus.IN_PROGRESS)
            ? this.createdAt : null;
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
        return create(roomId, propertyId, requestedByTenantId, title, description, priority, causeCategory, null);
    }

    /** Hạn xử lý {@code due_at} = lúc tạo + SLA của mức ưu tiên; sla null nghĩa là không đặt hạn. */
    public static MaintenanceTicket create(
        UUID roomId,
        UUID propertyId,
        UUID requestedByTenantId,
        String title,
        String description,
        MaintenancePriority priority,
        MaintenanceCause causeCategory,
        Duration sla
    ) {
        Instant now = Instant.now();
        MaintenanceTicket ticket = new MaintenanceTicket(
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
            null,
            sla == null ? null : now.plus(sla),
            null,
            now,
            now
        );
        ticket.pendingEvents.add(new MaintenanceTicketEvent(ticket.id, null, MaintenanceStatus.REPORTED, null,
            "Tạo phiếu", now));
        return ticket;
    }

    private void recordEvent(MaintenanceStatus from, String note) {
        pendingEvents.add(new MaintenanceTicketEvent(id, from, status, null, note, updatedAt));
    }

    /** Gắn người thực hiện cho các mốc chưa lưu (hệ thống tự chuyển trạng thái thì để null). */
    public void attributeEventsTo(UUID actorId) {
        for (int i = 0; i < pendingEvents.size(); i++) {
            pendingEvents.set(i, pendingEvents.get(i).withActor(actorId));
        }
    }

    public List<MaintenanceTicketEvent> drainEvents() {
        List<MaintenanceTicketEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    /** Quá hạn: còn việc phải làm (chưa sửa xong/đóng) và đã qua hạn xử lý. */
    public boolean isOverdue(Instant now) {
        return dueAt != null && !isWorkFinished() && now.isAfter(dueAt);
    }

    public void markSlaNotified(Instant now) {
        this.slaNotifiedAt = now;
    }

    public boolean isAssignmentTimeoutDue(Instant now, java.time.Duration timeout) {
        return assignedTechnicianId != null
            && (status == MaintenanceStatus.ASSIGNED || status == MaintenanceStatus.IN_PROGRESS)
            && assignedAt != null
            && understaffedNotifiedAt == null
            && !now.isBefore(assignedAt.plus(timeout));
    }

    public boolean isUnderstaffedCancellationDue(Instant now, java.time.Duration cancelAfter) {
        return (status == MaintenanceStatus.ASSIGNED || status == MaintenanceStatus.IN_PROGRESS)
            && understaffedNotifiedAt != null
            && !now.isBefore(understaffedNotifiedAt.plus(cancelAfter));
    }

    public void markUnderstaffed(Instant now) {
        if (assignedTechnicianId == null
            || (status != MaintenanceStatus.ASSIGNED && status != MaintenanceStatus.IN_PROGRESS)
            || assignedAt == null
            || understaffedNotifiedAt != null) {
            throw new MaintenanceTicketException("Phiếu không đủ điều kiện đánh dấu thiếu nhân lực");
        }
        understaffedNotifiedAt = now;
        updatedAt = now;
        recordEvent(status, "Phiếu thiếu nhân lực sau thời gian chờ giao việc");
    }

    public void autoCancelForUnderstaffing(Instant now) {
        if (understaffedNotifiedAt == null
            || (status != MaintenanceStatus.ASSIGNED && status != MaintenanceStatus.IN_PROGRESS)) {
            throw new MaintenanceTicketException("Phiếu không còn đủ điều kiện tự hủy do thiếu nhân lực");
        }
        MaintenanceStatus before = status;
        status = MaintenanceStatus.CANCELLED;
        updatedAt = now;
        recordEvent(before, "Hủy tự động do thiếu nhân lực");
    }

    /** Khôi phục các mốc giao việc đã lưu trong persistence adapter. */
    public void restoreAssignmentTimeoutState(Instant assignedAt, Instant understaffedNotifiedAt) {
        this.assignedAt = assignedAt;
        this.understaffedNotifiedAt = understaffedNotifiedAt;
    }

    public void assignTo(UUID technicianId) {
        if (technicianId == null) {
            throw new IllegalArgumentException("Technician id cannot be null");
        }
        if (this.status == MaintenanceStatus.RESOLVED || this.status == MaintenanceStatus.REJECTED
            || this.status == MaintenanceStatus.AWAITING_PAYMENT) {
            throw new MaintenanceTicketException("Không thể phân công kỹ thuật viên cho phiếu đang ở trạng thái " + status);
        }
        MaintenanceStatus before = this.status;
        this.assignedTechnicianId = technicianId;
        this.status = MaintenanceStatus.ASSIGNED;
        this.assignedAt = Instant.now();
        this.understaffedNotifiedAt = null;
        this.updatedAt = assignedAt;
        recordEvent(before, "Giao việc cho kỹ thuật viên");
    }

    /** Quản lý giao phiếu lần đầu hoặc giao lại phiếu đang được xử lý cho kỹ thuật viên khác. */
    public void assign(UUID technicianId) {
        assign(technicianId, null);
    }

    public void assign(UUID technicianId, String reason) {
        if (technicianId == null) {
            throw new IllegalArgumentException("Technician id cannot be null");
        }
        if (status != MaintenanceStatus.REPORTED && status != MaintenanceStatus.ASSIGNED
            && status != MaintenanceStatus.IN_PROGRESS) {
            throw new MaintenanceTicketException("Không thể giao kỹ thuật viên cho phiếu đang ở trạng thái " + status);
        }
        if (assignedTechnicianId == null && requestedByTenantId != null && !isBranchConfirmed()) {
            throw new InvalidMaintenanceStateException(
                "Cần chốt nhánh xử lý (hao mòn tự nhiên hoặc lỗi do người thuê) trước khi giao việc.");
        }
        UUID previousTechnicianId = assignedTechnicianId;
        if (previousTechnicianId != null && previousTechnicianId.equals(technicianId)) {
            throw new MaintenanceTicketException("Kỹ thuật viên này đang được giao xử lý phiếu");
        }
        boolean reassignment = previousTechnicianId != null;
        if (reassignment && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Cần ghi lý do giao lại kỹ thuật viên");
        }
        MaintenanceStatus before = status;
        assignedTechnicianId = technicianId;
        status = MaintenanceStatus.ASSIGNED;
        assignedAt = Instant.now();
        understaffedNotifiedAt = null;
        updatedAt = assignedAt;
        String note = reassignment
            ? "Giao lại cho kỹ thuật viên khác. Lý do: " + reason.trim()
            : "Giao việc cho kỹ thuật viên";
        recordEvent(before, note);
    }

    /** Từ chối phiếu chưa xử lý; bắt buộc có lý do để người thuê biết. */
    public void reject(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Lý do từ chối không được để trống");
        }
        if (this.status != MaintenanceStatus.REPORTED && this.status != MaintenanceStatus.ASSIGNED
            && this.status != MaintenanceStatus.PENDING_OWNER_APPROVAL) {
            throw new MaintenanceTicketException(
                "Chỉ từ chối được phiếu chưa xử lý (REPORTED/ASSIGNED) hoặc đang chờ chủ trọ duyệt, phiếu hiện ở " + status);
        }
        MaintenanceStatus before = this.status;
        this.rejectionReason = reason.trim();
        this.status = MaintenanceStatus.REJECTED;
        this.updatedAt = Instant.now();
        recordEvent(before, "Từ chối: " + this.rejectionReason);
    }

    public void accept(UUID technicianId) {
        if (technicianId == null) {
            throw new IllegalArgumentException("Technician id cannot be null");
        }
        if (this.status == MaintenanceStatus.RESOLVED || this.status == MaintenanceStatus.REJECTED
            || this.status == MaintenanceStatus.AWAITING_PAYMENT || this.status == MaintenanceStatus.VERIFIED
            || this.status == MaintenanceStatus.CANCELLED || isPendingApproval()) {
            throw new InvalidMaintenanceStateException("Không thể tiếp nhận phiếu đang ở trạng thái " + status);
        }
        assertMayWork();
        MaintenanceStatus before = this.status;
        this.assignedTechnicianId = technicianId;
        this.status = MaintenanceStatus.IN_PROGRESS;
        this.updatedAt = Instant.now();
        recordEvent(before, "Tiếp nhận và bắt đầu xử lý");
    }

    public void startWork() {
        if (this.status != MaintenanceStatus.ASSIGNED) {
            throw new MaintenanceTicketException("Phiếu phải được phân công kỹ thuật viên trước khi bắt đầu sửa.");
        }
        assertMayWork();
        this.status = MaintenanceStatus.IN_PROGRESS;
        this.updatedAt = Instant.now();
        recordEvent(MaintenanceStatus.ASSIGNED, "Bắt đầu xử lý");
    }

    public void resolve(String notes, BigDecimal cost) {
        if (this.status != MaintenanceStatus.ASSIGNED && this.status != MaintenanceStatus.IN_PROGRESS) {
            throw new MaintenanceTicketException("Chỉ hoàn tất được phiếu đã phân công hoặc đang xử lý; trạng thái hiện tại: " + status);
        }
        if (notes == null || notes.isBlank()) {
            throw new MaintenanceTicketException("Cần ghi kết quả xử lý trước khi hoàn tất phiếu.");
        }
        assertMayWork();
        BigDecimal effectiveCost = cost != null ? cost : this.repairCost;
        if (cost != null && cost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Chi phí sửa chữa không được âm.");
        }
        if (requiresQuoteApproval() && approvedQuoteTotal != null && effectiveCost.compareTo(approvedQuoteTotal) > 0) {
            throw new InvalidMaintenanceStateException(
                "Chi phí thực tế vượt báo giá đã được người thuê phê duyệt. Hãy gửi báo giá bổ sung phần phát sinh để người thuê phê duyệt.");
        }
        this.resolutionNotes = notes.trim();
        if (cost != null) {
            this.repairCost = cost;
        }
        if (this.repairCost.signum() > 0 && this.liableParty == LiableParty.UNDETERMINED) {
            throw new MaintenanceTicketException("Cần xác định bên chịu phí trước khi ghi nhận chi phí sửa chữa");
        }
        MaintenanceStatus before = this.status;
        this.status = chargesTenant() ? MaintenanceStatus.AWAITING_PAYMENT : MaintenanceStatus.RESOLVED;
        this.understaffedNotifiedAt = null;
        this.updatedAt = Instant.now();
        recordEvent(before, this.status == MaintenanceStatus.AWAITING_PAYMENT
            ? "Đã sửa xong, chờ người thuê thanh toán phí" : "Hoàn tất xử lý");
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

    /**
     * Nhập (hoặc cập nhật) báo giá khi phiếu chưa sửa xong. Báo giá không thay đổi trạng thái, chỉ ghi vào timeline;
     * chi phí thực tế vẫn chốt khi xử lý xong.
     */
    public void quote(BigDecimal amount, String note, UUID actorId) {
        if (requiresQuoteApproval()) {
            throw new InvalidMaintenanceStateException(
                "Phiếu lỗi do người thuê phải gửi báo giá chi tiết để người thuê phê duyệt.");
        }
        if (status != MaintenanceStatus.REPORTED && status != MaintenanceStatus.ASSIGNED
            && status != MaintenanceStatus.IN_PROGRESS) {
            throw new InvalidMaintenanceStateException("Chỉ báo giá được khi phiếu chưa sửa xong. Trạng thái hiện tại: " + status);
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Số tiền báo giá không được để trống hoặc âm.");
        }
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 1000) {
            throw new IllegalArgumentException("Ghi chú báo giá tối đa 1000 ký tự.");
        }
        this.updatedAt = Instant.now();
        this.quote = new Quote(amount, cleanNote, actorId, this.updatedAt);
        String amountText = java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("vi-VN"))
            .format(amount);
        recordEvent(status, "Báo giá " + amountText + " đ" + (cleanNote == null ? "" : ": " + cleanNote));
    }

    /** Người thuê xác nhận đã sửa đạt: phiếu đóng ở trạng thái VERIFIED. */
    public void verifyByTenant() {
        if (status != MaintenanceStatus.RESOLVED) {
            throw new InvalidMaintenanceStateException(
                "Chỉ xác nhận được khi phiếu đã sửa xong và không còn phí chờ thanh toán. Trạng thái hiện tại: " + status);
        }
        this.status = MaintenanceStatus.VERIFIED;
        this.updatedAt = Instant.now();
        recordEvent(MaintenanceStatus.RESOLVED, "Người thuê xác nhận đã sửa xong");
    }

    /**
     * Người thuê phản hồi chưa đạt: phiếu mở lại để sửa tiếp — về ASSIGNED nếu đã có kỹ thuật viên, ngược lại
     * REPORTED để quản lý giao việc.
     */
    public void reopenByTenant(String feedback) {
        if (status != MaintenanceStatus.RESOLVED && status != MaintenanceStatus.AWAITING_PAYMENT) {
            throw new InvalidMaintenanceStateException(
                "Chỉ phản hồi chưa đạt được khi phiếu đã sửa xong. Trạng thái hiện tại: " + status);
        }
        if (feedback == null || feedback.isBlank()) {
            throw new IllegalArgumentException("Cần nêu lý do chưa đạt.");
        }
        if (feedback.trim().length() > 1000) {
            throw new IllegalArgumentException("Phản hồi tối đa 1000 ký tự.");
        }
        MaintenanceStatus previous = status;
        this.status = assignedTechnicianId != null ? MaintenanceStatus.ASSIGNED : MaintenanceStatus.REPORTED;
        this.assignedAt = assignedTechnicianId != null ? Instant.now() : null;
        this.understaffedNotifiedAt = null;
        this.updatedAt = Instant.now();
        recordEvent(previous, "Người thuê phản hồi chưa đạt: " + feedback.trim());
    }

    /** Repository khôi phục báo giá đã lưu khi đọc phiếu. */
    public void restoreQuote(Quote quote) {
        this.quote = quote;
    }

    public Quote getQuote() {
        return quote;
    }

    /** Hóa đơn phí sửa chữa đã thanh toán đủ. */
    public void markFeePaid() {
        if (this.status != MaintenanceStatus.AWAITING_PAYMENT) {
            throw new MaintenanceTicketException("Phiếu không ở trạng thái chờ thanh toán: " + status);
        }
        this.status = MaintenanceStatus.RESOLVED;
        this.updatedAt = Instant.now();
        recordEvent(MaintenanceStatus.AWAITING_PAYMENT, "Người thuê đã thanh toán phí sửa chữa");
    }

    /** Quản lý miễn khoản phí: chủ trọ chịu, phiếu hoàn tất. */
    public void waiveFee() {
        if (this.status != MaintenanceStatus.AWAITING_PAYMENT) {
            throw new MaintenanceTicketException("Chỉ có thể miễn phí cho phiếu đang chờ thanh toán: " + status);
        }
        this.liableParty = LiableParty.OWNER;
        this.status = MaintenanceStatus.RESOLVED;
        this.updatedAt = Instant.now();
        recordEvent(MaintenanceStatus.AWAITING_PAYMENT, "Quản lý miễn phí sửa chữa");
    }

    public boolean isResolved() {
        return this.status == MaintenanceStatus.RESOLVED || this.status == MaintenanceStatus.VERIFIED;
    }

    /** Việc sửa đã xong (hoặc phiếu đã đóng): không còn chặn việc nhả phòng khỏi bảo trì. */
    public boolean isWorkFinished() {
        return isResolved()
            || this.status == MaintenanceStatus.AWAITING_PAYMENT
            || this.status == MaintenanceStatus.REJECTED
            || this.status == MaintenanceStatus.CANCELLED;
    }

    /** Phiếu chưa bắt đầu xử lý (vừa báo hoặc mới giao, kỹ thuật viên chưa nhận làm) thì còn sửa/hủy được (#89). */
    public boolean isEditable() {
        return this.status == MaintenanceStatus.REPORTED || this.status == MaintenanceStatus.ASSIGNED;
    }

    /**
     * Sửa nội dung phiếu chưa xử lý (#89). Đổi mức ưu tiên thì tính lại hạn xử lý từ lúc tạo phiếu theo SLA mới
     * ({@code sla} null là không đặt hạn) và cho phép cảnh báo quá hạn lại.
     */
    public void updateRequest(String newTitle, String newDescription, MaintenancePriority newPriority, Duration sla) {
        if (!isEditable()) {
            throw new InvalidMaintenanceStateException(
                "Chỉ sửa được phiếu chưa xử lý (vừa báo hoặc mới giao), phiếu hiện ở trạng thái " + status);
        }
        if (newTitle == null || newTitle.isBlank()) {
            throw new IllegalArgumentException("Tiêu đề không được để trống");
        }
        if (newDescription == null || newDescription.isBlank()) {
            throw new IllegalArgumentException("Mô tả không được để trống");
        }
        MaintenancePriority priorityAfter = newPriority != null ? newPriority : this.priority;
        if (priorityAfter != this.priority) {
            this.dueAt = sla == null ? null : createdAt.plus(sla);
            this.slaNotifiedAt = null;
        }
        this.title = newTitle.trim();
        this.description = newDescription.trim();
        this.priority = priorityAfter;
        this.updatedAt = Instant.now();
    }

    /** Hủy phiếu chưa xử lý (#89): chuyển CANCELLED, ghi mốc vào dòng thời gian, không xóa phiếu. */
    public void cancel(String reason) {
        if (!isEditable() && !isPendingApproval()) {
            throw new InvalidMaintenanceStateException(
                "Chỉ hủy được phiếu chưa xử lý (vừa báo, mới giao hoặc đang chờ duyệt), phiếu hiện ở trạng thái " + status);
        }
        MaintenanceStatus before = this.status;
        this.status = MaintenanceStatus.CANCELLED;
        this.updatedAt = Instant.now();
        recordEvent(before, reason == null || reason.isBlank() ? "Hủy phiếu" : "Hủy phiếu: " + reason.trim());
    }

    // ---- Chốt nhánh A/B và duyệt trước khi sửa (#202, #203) ----

    public boolean isPendingApproval() {
        return status == MaintenanceStatus.PENDING_OWNER_APPROVAL || status == MaintenanceStatus.PENDING_TENANT_APPROVAL;
    }

    public boolean isBranchConfirmed() {
        return branchConfirmedAt != null;
    }

    /** Nhánh A đã chốt: hao mòn tự nhiên, chủ trọ chịu phí và phải duyệt trước khi sửa. */
    public boolean isWearBranch() {
        return isBranchConfirmed() && liableParty == LiableParty.OWNER;
    }

    /** Nhánh B đã chốt: lỗi do người thuê, phải có báo giá được người thuê phê duyệt trước khi sửa. */
    public boolean requiresQuoteApproval() {
        return isBranchConfirmed() && liableParty == LiableParty.TENANT;
    }

    /**
     * Điều kiện được giao hoặc bắt đầu sửa: phiếu do người thuê báo phải được chốt nhánh; nhánh B phải có báo giá được
     * duyệt. Nhánh A chờ chủ trọ duyệt ở trạng thái riêng nên không sang được bước này khi chưa duyệt.
     */
    public void assertMayWork() {
        if (requestedByTenantId != null && !isBranchConfirmed()) {
            throw new InvalidMaintenanceStateException(
                "Cần chốt nhánh xử lý (hao mòn tự nhiên hoặc lỗi do người thuê) trước khi giao việc hoặc sửa.");
        }
        if (isPendingApproval()) {
            throw new InvalidMaintenanceStateException("Phiếu đang chờ phê duyệt, chưa được sửa.");
        }
        if (requiresQuoteApproval() && quoteApprovedAt == null) {
            throw new InvalidMaintenanceStateException("Cần người thuê phê duyệt báo giá trước khi sửa.");
        }
    }

    /** Đã đủ điều kiện để kỹ thuật viên bắt đầu sửa (cùng quy tắc với {@link #assertMayWork()}, không ném lỗi). */
    public boolean canStartWork() {
        return !(requestedByTenantId != null && !isBranchConfirmed())
            && !isPendingApproval()
            && !(requiresQuoteApproval() && quoteApprovedAt == null);
    }

    /**
     * Staff hoặc chủ trọ chốt (hoặc đổi) nhánh xử lý khi chưa bắt đầu sửa. Nhánh A (hao mòn, chủ trọ chịu) vào trạng thái
     * chờ chủ trọ duyệt có hạn; nhánh B (lỗi người thuê, người thuê chịu) chờ báo giá. Đổi nhánh phải ghi lý do.
     */
    public void confirmBranch(LiableParty branch, String reason, UUID actorId, Duration ownerApprovalWindow) {
        if (branch != LiableParty.OWNER && branch != LiableParty.TENANT) {
            throw new IllegalArgumentException(
                "Nhánh phải là hao mòn tự nhiên (chủ trọ chịu phí) hoặc lỗi do người thuê (người thuê chịu phí).");
        }
        if (status != MaintenanceStatus.REPORTED && status != MaintenanceStatus.ASSIGNED && !isPendingApproval()) {
            throw new InvalidMaintenanceStateException(
                "Chỉ chốt hoặc đổi nhánh được khi chưa bắt đầu sửa; phiếu hiện ở trạng thái " + status);
        }
        if (isBranchConfirmed() && liableParty == branch) {
            throw new InvalidMaintenanceStateException("Phiếu đã được chốt đúng nhánh này.");
        }
        LiableParty declared = causeCategory == null ? null : causeCategory.defaultLiableParty();
        boolean changed = isBranchConfirmed() || declared != branch;
        String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (changed && cleanReason == null) {
            throw new IllegalArgumentException("Cần ghi lý do khi chốt nhánh khác với nguyên nhân người thuê khai.");
        }
        if (cleanReason != null && cleanReason.length() > 500) {
            throw new IllegalArgumentException("Lý do tối đa 500 ký tự.");
        }

        MaintenanceStatus before = status;
        MaintenanceStatus base = isPendingApproval() ? resumeStatus : status;
        Instant now = Instant.now();
        this.liableParty = branch;
        this.branchConfirmedAt = now;
        this.branchConfirmedBy = actorId;
        this.approvalDueAt = null;
        this.ownerApprovedAt = null;
        this.approvalEscalatedAt = null;
        this.quoteDueAt = null;
        this.quoteApprovedAt = null;
        this.approvedQuoteTotal = null;
        this.negotiationRounds = 0;
        this.reminderSentAt = null;
        this.requoteRequested = false;
        this.resumeStatus = null;
        if (branch == LiableParty.OWNER) {
            this.resumeStatus = base;
            this.approvalDueAt = ownerApprovalWindow == null ? null : now.plus(ownerApprovalWindow);
            this.status = MaintenanceStatus.PENDING_OWNER_APPROVAL;
        } else {
            this.status = base;
        }
        this.updatedAt = now;
        String label = branch == LiableParty.OWNER
            ? "Chốt nhánh hao mòn tự nhiên (chủ trọ chịu phí), chờ chủ trọ duyệt"
            : "Chốt nhánh lỗi do người thuê (người thuê chịu phí), chờ báo giá";
        recordEvent(before, cleanReason == null ? label : label + ". Lý do: " + cleanReason);
    }

    /** Chủ trọ duyệt xử lý nhánh A: phiếu quay lại luồng giao việc. */
    public void ownerApprove() {
        if (status != MaintenanceStatus.PENDING_OWNER_APPROVAL) {
            throw new InvalidMaintenanceStateException(
                "Chỉ duyệt được phiếu đang chờ chủ trọ duyệt; phiếu hiện ở trạng thái " + status);
        }
        this.ownerApprovedAt = Instant.now();
        this.status = resumeStatus != null ? resumeStatus : MaintenanceStatus.REPORTED;
        this.resumeStatus = null;
        this.updatedAt = ownerApprovedAt;
        recordEvent(MaintenanceStatus.PENDING_OWNER_APPROVAL, "Chủ trọ duyệt xử lý, chi phí do chủ trọ chịu");
    }

    /**
     * Quá hạn duyệt: tự chuyển cho staff/kỹ thuật viên xử lý mà không cần chủ trọ duyệt. Chạy lặp không chuyển hai lần.
     */
    public boolean escalateOverdueApproval(Instant now) {
        if (status != MaintenanceStatus.PENDING_OWNER_APPROVAL || approvalDueAt == null || now.isBefore(approvalDueAt)) {
            return false;
        }
        this.approvalEscalatedAt = now;
        this.status = resumeStatus != null ? resumeStatus : MaintenanceStatus.REPORTED;
        this.resumeStatus = null;
        this.updatedAt = now;
        recordEvent(MaintenanceStatus.PENDING_OWNER_APPROVAL,
            "Quá hạn duyệt, hệ thống tự chuyển cho staff/kỹ thuật viên xử lý (MAINTENANCE_AUTO_ESCALATED)");
        return true;
    }

    /**
     * Gửi báo giá chi tiết (linh kiện và công sửa) cho người thuê phê duyệt, hạn {@code window} kể từ lúc gửi. Phiếu
     * đã có báo giá được duyệt thì đây là báo giá bổ sung phần phát sinh, bắt đầu đợt thương lượng mới.
     */
    public MaintenanceQuoteVersion submitQuote(
        List<MaintenanceQuoteVersion.Line> lines, BigDecimal laborAmount, String note, UUID actorId, Duration window
    ) {
        if (!requiresQuoteApproval()) {
            throw new InvalidMaintenanceStateException(
                "Chỉ lập báo giá chi tiết cho phiếu đã được chốt là lỗi do người thuê.");
        }
        if (status != MaintenanceStatus.REPORTED && status != MaintenanceStatus.ASSIGNED
            && status != MaintenanceStatus.IN_PROGRESS) {
            throw new InvalidMaintenanceStateException(
                "Chỉ gửi báo giá khi phiếu chưa sửa xong và không đang chờ người thuê phê duyệt; phiếu hiện ở " + status);
        }
        List<MaintenanceQuoteVersion.Line> cleanLines = lines == null ? List.of() : List.copyOf(lines);
        BigDecimal labor = laborAmount == null ? BigDecimal.ZERO : laborAmount;
        if (labor.signum() < 0) {
            throw new IllegalArgumentException("Công sửa không được âm.");
        }
        BigDecimal total = MaintenanceQuoteVersion.total(cleanLines, labor);
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("Báo giá phải có ít nhất một khoản (linh kiện hoặc công sửa) lớn hơn 0.");
        }
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 1000) {
            throw new IllegalArgumentException("Ghi chú báo giá tối đa 1000 ký tự.");
        }
        Instant now = Instant.now();
        MaintenanceStatus before = status;
        if (quoteApprovedAt != null) {
            negotiationRounds = 0;
        }
        this.quoteVersion++;
        this.requoteRequested = false;
        this.resumeStatus = status;
        this.status = MaintenanceStatus.PENDING_TENANT_APPROVAL;
        this.quoteDueAt = window == null ? null : now.plus(window);
        this.reminderSentAt = null;
        this.quote = new Quote(total, cleanNote, actorId, now);
        this.updatedAt = now;
        recordEvent(before, "Gửi báo giá phiên bản " + quoteVersion + " (" + vnd(total) + "), chờ người thuê phê duyệt");
        return new MaintenanceQuoteVersion(UUID.randomUUID(), id, quoteVersion, cleanLines, labor, total, cleanNote,
            actorId, now, MaintenanceQuoteVersion.Status.PENDING, null, null);
    }

    /** Người thuê phê duyệt báo giá: phiếu sang bước sửa. Phê duyệt hai lần trả 409. */
    public void tenantApproveQuote() {
        if (status != MaintenanceStatus.PENDING_TENANT_APPROVAL) {
            throw new InvalidMaintenanceStateException(
                "Báo giá không ở trạng thái chờ phê duyệt; phiếu hiện ở trạng thái " + status);
        }
        Instant now = Instant.now();
        // Báo giá bổ sung chỉ gồm phần phát sinh: trần chi phí = mức đã duyệt trước đó + phần phát sinh được duyệt.
        this.approvedQuoteTotal = quoteApprovedAt != null && approvedQuoteTotal != null
            ? approvedQuoteTotal.add(quote.amount()) : quote.amount();
        this.quoteApprovedAt = now;
        this.quoteDueAt = null;
        this.status = resumeStatus != null ? resumeStatus : MaintenanceStatus.REPORTED;
        this.resumeStatus = null;
        this.updatedAt = now;
        recordEvent(MaintenanceStatus.PENDING_TENANT_APPROVAL,
            "Người thuê phê duyệt báo giá phiên bản " + quoteVersion + " (" + vnd(quote.amount()) + "), tổng được duyệt "
                + vnd(approvedQuoteTotal));
    }

    /** Người thuê thương lượng: tối đa {@value #MAX_NEGOTIATION_ROUNDS} vòng, sau đó chỉ còn phê duyệt hoặc để hết hạn. */
    public void tenantNegotiateQuote(String tenantNote) {
        if (status != MaintenanceStatus.PENDING_TENANT_APPROVAL) {
            throw new InvalidMaintenanceStateException(
                "Báo giá không ở trạng thái chờ phê duyệt; phiếu hiện ở trạng thái " + status);
        }
        if (negotiationRounds >= MAX_NEGOTIATION_ROUNDS) {
            throw new InvalidMaintenanceStateException(
                "Đã hết " + MAX_NEGOTIATION_ROUNDS + " vòng thương lượng; chỉ còn phê duyệt hoặc để hết hạn.");
        }
        if (tenantNote == null || tenantNote.isBlank()) {
            throw new IllegalArgumentException("Cần ghi đề xuất điều chỉnh (giảm hoặc bỏ khoản nào).");
        }
        String clean = tenantNote.trim();
        if (clean.length() > 1000) {
            throw new IllegalArgumentException("Phản hồi tối đa 1000 ký tự.");
        }
        this.negotiationRounds++;
        this.requoteRequested = true;
        this.quoteDueAt = null;
        this.status = resumeStatus != null ? resumeStatus : MaintenanceStatus.REPORTED;
        this.resumeStatus = null;
        this.updatedAt = Instant.now();
        recordEvent(MaintenanceStatus.PENDING_TENANT_APPROVAL, "Người thuê đề nghị điều chỉnh báo giá (vòng "
            + negotiationRounds + "/" + MAX_NEGOTIATION_ROUNDS + "): " + clean);
    }

    /** Quá hạn phê duyệt báo giá: tự hủy phiếu, không phát sinh phí cho người thuê. Chạy lặp không hủy hai lần. */
    public boolean expireQuote(Instant now) {
        if (status != MaintenanceStatus.PENDING_TENANT_APPROVAL || quoteDueAt == null || now.isBefore(quoteDueAt)) {
            return false;
        }
        this.status = MaintenanceStatus.CANCELLED;
        this.resumeStatus = null;
        this.updatedAt = now;
        recordEvent(MaintenanceStatus.PENDING_TENANT_APPROVAL,
            "Hủy tự động: người thuê không phê duyệt báo giá trong hạn (MAINTENANCE_QUOTE_EXPIRED)");
        return true;
    }

    /** Hạn của bước đang chờ duyệt (chủ trọ hoặc người thuê); null nếu không chờ duyệt. */
    public Instant pendingDeadline() {
        return switch (status) {
            case PENDING_OWNER_APPROVAL -> approvalDueAt;
            case PENDING_TENANT_APPROVAL -> quoteDueAt;
            default -> null;
        };
    }

    /** Sắp đến hạn duyệt và chưa nhắc: dùng cho job nhắc trước hạn. */
    public boolean isApprovalReminderDue(Instant now, Duration before) {
        Instant deadline = pendingDeadline();
        return deadline != null && reminderSentAt == null && now.isBefore(deadline)
            && !now.isBefore(deadline.minus(before));
    }

    public void markApprovalReminderSent(Instant now) {
        this.reminderSentAt = now;
    }

    /** Người thuê đã đề nghị điều chỉnh, đang chờ người lập báo giá gửi lại. */
    public boolean isAwaitingRequote() {
        return requoteRequested && requiresQuoteApproval() && !isPendingApproval();
    }

    public ApprovalState approvalState() {
        return new ApprovalState(branchConfirmedAt, branchConfirmedBy, approvalDueAt, ownerApprovedAt,
            approvalEscalatedAt, quoteDueAt, quoteApprovedAt, approvedQuoteTotal, negotiationRounds, quoteVersion,
            resumeStatus, reminderSentAt, requoteRequested);
    }

    /** Repository khôi phục trạng thái duyệt đã lưu khi đọc phiếu. */
    public void restoreApprovalState(ApprovalState state) {
        if (state == null) {
            return;
        }
        this.branchConfirmedAt = state.branchConfirmedAt();
        this.branchConfirmedBy = state.branchConfirmedBy();
        this.approvalDueAt = state.approvalDueAt();
        this.ownerApprovedAt = state.ownerApprovedAt();
        this.approvalEscalatedAt = state.approvalEscalatedAt();
        this.quoteDueAt = state.quoteDueAt();
        this.quoteApprovedAt = state.quoteApprovedAt();
        this.approvedQuoteTotal = state.approvedQuoteTotal();
        this.negotiationRounds = state.negotiationRounds();
        this.quoteVersion = state.quoteVersion();
        this.resumeStatus = state.resumeStatus();
        this.reminderSentAt = state.reminderSentAt();
        this.requoteRequested = state.requoteRequested();
    }

    private static String vnd(BigDecimal amount) {
        return java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("vi-VN")).format(amount) + " đ";
    }

    public Instant getBranchConfirmedAt() { return branchConfirmedAt; }
    public UUID getBranchConfirmedBy() { return branchConfirmedBy; }
    public Instant getApprovalDueAt() { return approvalDueAt; }
    public Instant getOwnerApprovedAt() { return ownerApprovedAt; }
    public Instant getApprovalEscalatedAt() { return approvalEscalatedAt; }
    public Instant getQuoteDueAt() { return quoteDueAt; }
    public Instant getQuoteApprovedAt() { return quoteApprovedAt; }
    public BigDecimal getApprovedQuoteTotal() { return approvedQuoteTotal; }
    public int getNegotiationRounds() { return negotiationRounds; }
    public int getQuoteVersion() { return quoteVersion; }

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

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getUnderstaffedNotifiedAt() {
        return understaffedNotifiedAt;
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

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getSlaNotifiedAt() {
        return slaNotifiedAt;
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

    /** Phiên bản dùng cho khóa lạc quan; do tầng lưu trữ gán sau khi đọc/ghi. */
    public long getVersion() {
        return version;
    }

    public void restoreVersion(long version) {
        this.version = version;
    }
}
