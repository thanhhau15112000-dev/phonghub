package com.phonghub.application.service;

import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.domain.model.MaintenanceQuoteVersion;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Xử lý quá hạn duyệt của một phiếu trong transaction của người gọi: nhánh A quá hạn chủ trọ duyệt thì tự chuyển cho
 * staff/kỹ thuật viên xử lý (#202); nhánh B quá hạn người thuê phê duyệt báo giá thì tự hủy (#203); nhắc trước hạn.
 * Mỗi mốc chỉ xử lý một lần nên chạy lặp job là an toàn.
 */
public class MaintenanceApprovalService {

    public enum ProcessResult {
        NO_ACTION,
        ESCALATED,
        QUOTE_EXPIRED,
        REMINDED
    }

    private static final DateTimeFormatter DEADLINE_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final MaintenanceTicketRepositoryPort ticketRepository;
    private final MaintenanceNotifier notifier;
    private final AuditPort auditPort;
    private final MaintenanceApprovalPolicy policy;

    public MaintenanceApprovalService(
        MaintenanceTicketRepositoryPort ticketRepository,
        MaintenanceNotifier notifier,
        AuditPort auditPort,
        MaintenanceApprovalPolicy policy
    ) {
        this.ticketRepository = Objects.requireNonNull(ticketRepository, "ticketRepository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.auditPort = Objects.requireNonNull(auditPort, "auditPort");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public ProcessResult processTicket(UUID ticketId, Instant now) {
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(now, "now");
        MaintenanceTicket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null || !ticket.isPendingApproval()) {
            return ProcessResult.NO_ACTION;
        }
        Instant deadline = ticket.pendingDeadline();

        if (ticket.escalateOverdueApproval(now)) {
            ticketRepository.save(ticket);
            String message = "Phiếu \"" + ticket.getTitle() + "\" quá hạn chủ trọ duyệt (" + format(deadline)
                + ") nên được tự chuyển cho nhân viên/kỹ thuật viên xử lý. Chi phí do chủ trọ chịu.";
            notifier.notifyManagers(ticket, "Phiếu bảo trì quá hạn duyệt", message);
            notifier.notifyTenant(ticket, "Phiếu bảo trì được chuyển xử lý",
                "Phiếu \"" + ticket.getTitle() + "\" đã được chuyển cho nhân viên/kỹ thuật viên xử lý.");
            auditPort.recordEvent(AuditPort.AuditEvent.of("MAINTENANCE_AUTO_ESCALATED", null, "MAINTENANCE_TICKET",
                ticket.getId().toString(), Map.of("approvalDueAt", deadline.toString())));
            return ProcessResult.ESCALATED;
        }

        if (ticket.expireQuote(now)) {
            ticketRepository.save(ticket);
            expireLatestPendingVersion(ticket, now);
            String message = "Phiếu \"" + ticket.getTitle() + "\" đã bị hủy tự động vì báo giá không được người thuê "
                + "phê duyệt trước " + format(deadline) + ". Không phát sinh khoản phí nào.";
            notifier.notifyTenant(ticket, "Phiếu bảo trì bị hủy do hết hạn phê duyệt báo giá", message);
            notifier.notifyManagers(ticket, "Phiếu bảo trì bị hủy do hết hạn phê duyệt báo giá", message);
            notifier.notifyTechnician(ticket, "Phiếu bảo trì bị hủy do hết hạn phê duyệt báo giá", message);
            auditPort.recordEvent(AuditPort.AuditEvent.of("MAINTENANCE_QUOTE_EXPIRED", null, "MAINTENANCE_TICKET",
                ticket.getId().toString(), Map.of("quoteDueAt", deadline.toString())));
            return ProcessResult.QUOTE_EXPIRED;
        }

        boolean ownerStep = ticket.getStatus() == MaintenanceStatus.PENDING_OWNER_APPROVAL;
        if (ticket.isApprovalReminderDue(now, ownerStep ? policy.ownerReminderBefore() : policy.quoteReminderBefore())) {
            ticket.markApprovalReminderSent(now);
            ticketRepository.save(ticket);
            if (ownerStep) {
                notifier.notifyOwner(ticket, "Nhắc duyệt phiếu bảo trì", "Phiếu \"" + ticket.getTitle()
                    + "\" cần bạn duyệt trước " + format(deadline) + ". Quá hạn, phiếu sẽ tự chuyển cho nhân viên xử lý.");
            } else {
                notifier.notifyTenant(ticket, "Nhắc phê duyệt báo giá", "Báo giá phiếu \"" + ticket.getTitle()
                    + "\" cần bạn phê duyệt trước " + format(deadline) + ". Quá hạn, yêu cầu sẽ bị hủy.");
            }
            return ProcessResult.REMINDED;
        }
        return ProcessResult.NO_ACTION;
    }

    private void expireLatestPendingVersion(MaintenanceTicket ticket, Instant now) {
        ticketRepository.findQuoteVersions(ticket.getId()).stream()
            .filter(version -> version.status() == MaintenanceQuoteVersion.Status.PENDING)
            .reduce((first, second) -> second)
            .ifPresent(version -> ticketRepository.saveQuoteVersion(
                version.decided(MaintenanceQuoteVersion.Status.EXPIRED, null, now)));
    }

    private static String format(Instant instant) {
        return DEADLINE_FORMAT.format(instant);
    }
}
