package com.phonghub.application.service;

import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.NotificationRepositoryPort;
import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.application.port.out.UserRepositoryPort;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Notification;
import com.phonghub.domain.model.Property;
import com.phonghub.domain.model.User;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Báo phiếu bảo trì quá hạn xử lý (SLA). Mỗi phiếu chỉ báo một lần ({@code sla_notified_at}); người nhận là chủ nhà trọ
 * và kỹ thuật viên đang được giao. Không gắn với người dùng nên chỉ gọi từ job định kỳ.
 */
public class MaintenanceSlaService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceSlaService.class);

    private final MaintenanceTicketRepositoryPort ticketRepository;
    private final PropertyRepositoryPort propertyRepository;
    private final UserRepositoryPort userRepository;
    private final NotificationRepositoryPort notificationRepository;
    private final AuditPort auditPort;

    public MaintenanceSlaService(
        MaintenanceTicketRepositoryPort ticketRepository,
        PropertyRepositoryPort propertyRepository,
        UserRepositoryPort userRepository,
        NotificationRepositoryPort notificationRepository,
        AuditPort auditPort
    ) {
        this.ticketRepository = ticketRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.auditPort = auditPort;
    }

    /** Trả về số phiếu vừa được báo quá hạn. Lỗi ở một phiếu không chặn các phiếu còn lại. */
    public int notifyOverdue(Instant now) {
        int notified = 0;
        for (MaintenanceTicket ticket : ticketRepository.findAll()) {
            if (ticket.getSlaNotifiedAt() != null || !ticket.isOverdue(now)) {
                continue;
            }
            try {
                notifyTicket(ticket, now);
                notified++;
            } catch (RuntimeException ex) {
                log.error("Không báo được phiếu quá hạn {}", ticket.getId(), ex);
            }
        }
        return notified;
    }

    private void notifyTicket(MaintenanceTicket ticket, Instant now) {
        Set<UUID> recipients = new LinkedHashSet<>();
        propertyRepository.findById(ticket.getPropertyId()).map(Property::ownerId).ifPresent(recipients::add);
        if (ticket.getAssignedTechnicianId() != null) {
            recipients.add(ticket.getAssignedTechnicianId());
        }
        String message = "Phiếu bảo trì \"" + ticket.getTitle() + "\" đã quá hạn xử lý (hạn: " + ticket.getDueAt() + ").";
        for (UUID recipientId : recipients) {
            User user = userRepository.findById(recipientId).orElse(null);
            if (user != null) {
                notificationRepository.save(Notification.createGeneral("Phiếu bảo trì quá hạn", message,
                    user.id(), user.username(), user.fullName(), user.role()));
            }
        }
        ticket.markSlaNotified(now);
        ticketRepository.save(ticket);
        auditPort.recordEvent(AuditPort.AuditEvent.of("MAINTENANCE_SLA_BREACHED", null, "MAINTENANCE_TICKET",
            ticket.getId().toString(), Map.of("recipients", recipients.size())));
    }
}
