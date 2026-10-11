package com.phonghub.application.service;

import com.phonghub.application.port.in.MaintenanceAttachmentUseCase;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.MaintenanceAttachmentRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.exception.AttachmentLimitException;
import com.phonghub.domain.exception.MaintenanceTicketException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.MaintenanceAttachment;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MaintenanceAttachmentService implements MaintenanceAttachmentUseCase {

    private static final Duration SIGNED_URL_TTL = Duration.ofMinutes(10);

    private final MaintenanceAttachmentRepositoryPort attachmentRepository;
    private final MaintenanceTicketRepositoryPort ticketRepository;
    private final TenantRepositoryPort tenantRepository;
    private final FileStoragePort storagePort;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final AuditPort auditPort;
    private final int maxImages;
    private final long maxImageBytes;
    private final long maxVideoBytes;

    public MaintenanceAttachmentService(
        MaintenanceAttachmentRepositoryPort attachmentRepository,
        MaintenanceTicketRepositoryPort ticketRepository,
        TenantRepositoryPort tenantRepository,
        FileStoragePort storagePort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        AuditPort auditPort,
        int maxImages,
        long maxImageBytes,
        long maxVideoBytes
    ) {
        this.attachmentRepository = attachmentRepository;
        this.ticketRepository = ticketRepository;
        this.tenantRepository = tenantRepository;
        this.storagePort = storagePort;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        this.auditPort = auditPort;
        this.maxImages = maxImages;
        this.maxImageBytes = maxImageBytes;
        this.maxVideoBytes = maxVideoBytes;
    }

    @Override
    public MaintenanceAttachment upload(UUID ticketId, String contentType, byte[] content) {
        CurrentUser user = currentUserPort.getCurrentUser();
        MaintenanceTicket ticket = findTicket(ticketId);
        assertCanUpload(user, ticket);
        if (ticket.isWorkFinished()) {
            throw new MaintenanceTicketException("Không đính kèm thêm được vào phiếu đã xử lý xong hoặc đã đóng");
        }

        boolean video = FileSanitizer.isVideo(contentType);
        if (!video && !FileSanitizer.isImage(contentType)) {
            throw new IllegalArgumentException("Chỉ nhận ảnh jpeg/png/webp hoặc video mp4/webm/mov");
        }
        List<MaintenanceAttachment> existing = attachmentRepository.findByTicketId(ticketId);
        if (video) {
            if (existing.stream().anyMatch(a -> a.kind() == MaintenanceAttachment.Kind.VIDEO)) {
                throw new AttachmentLimitException("Phiếu chỉ được đính kèm một video");
            }
        } else if (existing.stream().filter(a -> a.kind() == MaintenanceAttachment.Kind.IMAGE).count() >= maxImages) {
            throw new AttachmentLimitException("Mỗi phiếu chỉ được đính kèm tối đa " + maxImages + " ảnh");
        }

        FileSanitizer.SanitizedFile file = video
            ? FileSanitizer.sanitizeVideo(contentType, content, maxVideoBytes)
            : FileSanitizer.sanitize(contentType, content, maxImageBytes);
        String key = PrivateFileStorageService.objectKey(ticket.getPropertyId(), "maintenance", file.extension());
        storagePort.put(key, file.contentType(), file.content());
        MaintenanceAttachment saved;
        try {
            saved = attachmentRepository.save(new MaintenanceAttachment(UUID.randomUUID(), ticketId,
                video ? MaintenanceAttachment.Kind.VIDEO : MaintenanceAttachment.Kind.IMAGE, key, file.contentType(),
                file.content().length, user.id(), null));
        } catch (RuntimeException ex) {
            deleteQuietly(key);
            throw ex;
        }
        auditPort.recordEvent(AuditPort.AuditEvent.of("MAINTENANCE_ATTACHMENT_ADDED", user.id(), "MAINTENANCE_TICKET",
            ticketId.toString(), Map.of("kind", saved.kind().name(), "size", saved.sizeBytes())));
        return saved;
    }

    @Override
    public List<MaintenanceAttachment> list(UUID ticketId) {
        assertCanView(currentUserPort.getCurrentUser(), findTicket(ticketId));
        return attachmentRepository.findByTicketId(ticketId);
    }

    @Override
    public URI signedUrl(UUID ticketId, UUID attachmentId) {
        assertCanView(currentUserPort.getCurrentUser(), findTicket(ticketId));
        return storagePort.createSignedUrl(find(ticketId, attachmentId).storageKey(), SIGNED_URL_TTL);
    }

    @Override
    public void delete(UUID ticketId, UUID attachmentId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        MaintenanceTicket ticket = findTicket(ticketId);
        MaintenanceAttachment attachment = find(ticketId, attachmentId);
        boolean uploader = user != null && user.id().equals(attachment.uploadedBy());
        if (!uploader) {
            authorizationService.assertCanManageProperty(user, ticket.getPropertyId());
        } else {
            assertCanView(user, ticket);
        }
        attachmentRepository.delete(attachmentId);
        deleteQuietly(attachment.storageKey());
        auditPort.recordEvent(AuditPort.AuditEvent.of("MAINTENANCE_ATTACHMENT_REMOVED", user.id(), "MAINTENANCE_TICKET",
            ticketId.toString(), Map.of("kind", attachment.kind().name())));
    }

    /** Người thuê đã tạo phiếu (hoặc đang ở phòng đó) và owner/staff/admin của nhà trọ được tải lên. */
    private void assertCanUpload(CurrentUser user, MaintenanceTicket ticket) {
        if (user != null && user.role() == UserRole.TENANT) {
            assertCanView(user, ticket);
            return;
        }
        authorizationService.assertCanManageProperty(user, ticket.getPropertyId());
    }

    /** Xem: tenant của phiếu, owner/staff/admin của nhà trọ, technician được giao phiếu này. */
    private void assertCanView(CurrentUser user, MaintenanceTicket ticket) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException("Authentication required");
        }
        if (user.role() == UserRole.TENANT) {
            Tenant tenant = tenantRepository.findByUserId(user.id()).orElse(null);
            if (tenant == null || !authorizationService.canTenantViewTicket(user, ticket.getRoomId(),
                ticket.getRequestedByTenantId())) {
                throw new UnauthorizedPropertyAccessException("Tenant can only access attachments of their own tickets");
            }
            return;
        }
        if (user.role() == UserRole.TECHNICIAN) {
            authorizationService.assertTechnicianCanWorkOnProperty(user, ticket.getPropertyId());
            if (!user.id().equals(ticket.getAssignedTechnicianId())) {
                throw new UnauthorizedPropertyAccessException("Phiếu bảo trì này chưa được giao cho bạn");
            }
            return;
        }
        authorizationService.assertCanManageProperty(user, ticket.getPropertyId());
    }

    private MaintenanceTicket findTicket(UUID ticketId) {
        return ticketRepository.findById(ticketId)
            .orElseThrow(() -> new MaintenanceTicketException("Maintenance ticket not found with ID: " + ticketId));
    }

    private MaintenanceAttachment find(UUID ticketId, UUID attachmentId) {
        return attachmentRepository.findById(attachmentId)
            .filter(attachment -> attachment.ticketId().equals(ticketId))
            .orElseThrow(() -> new MaintenanceTicketException("Attachment not found: " + attachmentId));
    }

    private void deleteQuietly(String key) {
        try {
            storagePort.delete(key);
        } catch (RuntimeException ignored) {
            // best effort
        }
    }
}
