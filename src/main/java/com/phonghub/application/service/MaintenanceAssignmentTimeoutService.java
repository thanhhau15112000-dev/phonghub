package com.phonghub.application.service;

import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.StaffPropertyAssignment;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.User;
import com.phonghub.domain.model.UserRole;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Handles one overdue technician assignment within the caller's transaction (#223). */
public class MaintenanceAssignmentTimeoutService {

    public enum ProcessResult {
        NO_ACTION,
        MARKED_UNDERSTAFFED,
        AUTO_CANCELLED
    }

    private static final String UNDERSTAFFED_REASON = "Hủy tự động do thiếu nhân lực";

    private final MaintenanceTicketRepositoryPort ticketRepository;
    private final PropertyRepositoryPort propertyRepository;
    private final StaffPropertyAssignmentPort assignmentPort;
    private final TenantRepositoryPort tenantRepository;
    private final UserRepositoryPort userRepository;
    private final NotificationRepositoryPort notificationRepository;
    private final AuditPort auditPort;
    private final MaintenanceAssignmentTimeoutPolicy policy;

    public MaintenanceAssignmentTimeoutService(
        MaintenanceTicketRepositoryPort ticketRepository,
        PropertyRepositoryPort propertyRepository,
        StaffPropertyAssignmentPort assignmentPort,
        TenantRepositoryPort tenantRepository,
        UserRepositoryPort userRepository,
        NotificationRepositoryPort notificationRepository,
        AuditPort auditPort,
        MaintenanceAssignmentTimeoutPolicy policy
    ) {
        this.ticketRepository = Objects.requireNonNull(ticketRepository, "ticketRepository");
        this.propertyRepository = Objects.requireNonNull(propertyRepository, "propertyRepository");
        this.assignmentPort = Objects.requireNonNull(assignmentPort, "assignmentPort");
        this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository");
        this.auditPort = Objects.requireNonNull(auditPort, "auditPort");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public ProcessResult processTicket(UUID ticketId, Instant now) {
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(now, "now");
        MaintenanceTicket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null) {
            return ProcessResult.NO_ACTION;
        }

        if (ticket.getUnderstaffedNotifiedAt() != null) {
            if (!ticket.isUnderstaffedCancellationDue(now, policy.understaffedCancelAfter())) {
                return ProcessResult.NO_ACTION;
            }
            ticket.autoCancelForUnderstaffing(now);
            ticketRepository.save(ticket);
            notifyTenantAndManagers(ticket, "Phiếu bảo trì bị hủy tự động",
                "Phiếu bảo trì \"" + ticket.getTitle() + "\" đã bị hủy tự động do thiếu nhân lực sau "
                    + formatDuration(policy.understaffedCancelAfter()) + " mà chưa được giao lại hoặc hoàn tất.");
            auditPort.recordEvent(AuditPort.AuditEvent.of("MAINTENANCE_TICKET_AUTO_CANCELLED", null,
                "MAINTENANCE_TICKET", ticket.getId().toString(),
                Map.of("reason", UNDERSTAFFED_REASON, "assignedAt", ticket.getAssignedAt().toString())));
            return ProcessResult.AUTO_CANCELLED;
        }

        if (!ticket.isAssignmentTimeoutDue(now, policy.assignmentTimeout())) {
            return ProcessResult.NO_ACTION;
        }

        ticket.markUnderstaffed(now);
        ticketRepository.save(ticket);
        int recipients = notifyUnderstaffed(ticket);
        auditPort.recordEvent(AuditPort.AuditEvent.of("MAINTENANCE_TICKET_UNDERSTAFFED", null,
            "MAINTENANCE_TICKET", ticket.getId().toString(),
            Map.of("assignedAt", ticket.getAssignedAt().toString(), "notifiedAt", now.toString(),
                "recipientCount", recipients)));
        return ProcessResult.MARKED_UNDERSTAFFED;
    }

    private int notifyUnderstaffed(MaintenanceTicket ticket) {
        String message = "Phiếu bảo trì \"" + ticket.getTitle() + "\" đã được giao hơn "
            + formatDuration(policy.assignmentTimeout()) + " nhưng chưa được xử lý và đang thiếu nhân lực. "
            + "Việc sửa chữa có thể chậm.";
        int recipientCount = notifyTenant(ticket, "Phiếu bảo trì thiếu nhân lực", message);

        Set<String> alternatives = alternativeTechnicianNames(ticket);
        String alternativesMessage = alternatives.isEmpty()
            ? " Hiện chưa có kỹ thuật viên khác được phân công vào nhà trọ."
            : " Có thể giao lại cho kỹ thuật viên khác: " + String.join(", ", alternatives) + ".";
        return recipientCount + notifyManagers(ticket, "Phiếu bảo trì thiếu nhân lực", message + alternativesMessage);
    }

    private int notifyTenantAndManagers(MaintenanceTicket ticket, String title, String message) {
        int recipients = notifyTenant(ticket, title, message);
        return recipients + notifyManagers(ticket, title, message);
    }

    private int notifyTenant(MaintenanceTicket ticket, String title, String message) {
        if (ticket.getRequestedByTenantId() == null) {
            return 0;
        }
        Optional<User> recipient = tenantRepository.findById(ticket.getRequestedByTenantId())
            .map(Tenant::userId)
            .filter(Objects::nonNull)
            .flatMap(userRepository::findById);
        recipient.ifPresent(user -> send(user, title, message, ticket));
        return recipient.isPresent() ? 1 : 0;
    }

    private int notifyManagers(MaintenanceTicket ticket, String title, String message) {
        Map<UUID, User> recipients = managerRecipients(ticket);
        recipients.values().forEach(user -> send(user, title, message, ticket));
        return recipients.size();
    }

    private Map<UUID, User> managerRecipients(MaintenanceTicket ticket) {
        Map<UUID, User> recipients = new LinkedHashMap<>();
        propertyRepository.findById(ticket.getPropertyId()).map(Property::ownerId)
            .filter(Objects::nonNull)
            .flatMap(userRepository::findById)
            .ifPresent(owner -> recipients.put(owner.id(), owner));
        assignmentPort.findByPropertyId(ticket.getPropertyId()).stream()
            .map(StaffPropertyAssignment::staffUserId)
            .map(userRepository::findById)
            .flatMap(Optional::stream)
            .filter(user -> user.role() == UserRole.STAFF && user.status() == User.UserStatus.ACTIVE)
            .forEach(staff -> recipients.putIfAbsent(staff.id(), staff));
        return recipients;
    }

    private Set<String> alternativeTechnicianNames(MaintenanceTicket ticket) {
        Set<String> names = new LinkedHashSet<>();
        assignmentPort.findByPropertyId(ticket.getPropertyId()).stream()
            .map(StaffPropertyAssignment::staffUserId)
            .filter(id -> !id.equals(ticket.getAssignedTechnicianId()))
            .map(userRepository::findById)
            .flatMap(Optional::stream)
            .filter(user -> user.role() == UserRole.TECHNICIAN && user.status() == User.UserStatus.ACTIVE)
            .map(User::fullName)
            .forEach(names::add);
        return names;
    }

    private void send(User user, String title, String message, MaintenanceTicket ticket) {
        notificationRepository.save(Notification.createForUser(title, message, user,
            "/maintenance/" + ticket.getId()));
    }

    private static String formatDuration(Duration duration) {
        long totalMinutes = duration.toMinutes();
        long days = totalMinutes / (24 * 60);
        long hours = totalMinutes / 60 % 24;
        long minutes = totalMinutes % 60;
        if (days > 0) {
            return hours == 0 ? days + " ngày" : days + " ngày " + hours + " giờ";
        }
        if (hours > 0) {
            return minutes == 0 ? hours + " giờ" : hours + " giờ " + minutes + " phút";
        }
        return Math.max(1, minutes) + " phút";
    }
}
