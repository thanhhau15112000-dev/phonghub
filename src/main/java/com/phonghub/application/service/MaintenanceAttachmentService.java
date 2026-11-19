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
import com.phonghub.domain.exception.FileStorageUnavailableException;
import com.phonghub.domain.exception.MaintenanceTicketException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.MaintenanceAttachment;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.Tenant;
import com.phonghub.domain.model.UserRole;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaintenanceAttachmentService implements MaintenanceAttachmentUseCase {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceAttachmentService.class);
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
        return uploadAll(ticketId, List.of(new AttachmentFile(contentType, content))).getFirst();
    }

    @Override
    public List<MaintenanceAttachment> uploadAll(UUID ticketId, List<AttachmentFile> files) {
        return uploadAll(ticketId, files, MaintenanceAttachment.Phase.REPORT);
    }

    @Override
    public List<MaintenanceAttachment> uploadAll(UUID ticketId, List<AttachmentFile> files, MaintenanceAttachment.Phase phase) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Cần chọn ít nhất một tệp để tải lên.");
        }
        MaintenanceAttachment.Phase target = phase == null ? MaintenanceAttachment.Phase.REPORT : phase;
        CurrentUser user = currentUserPort.getCurrentUser();
        MaintenanceTicket ticket = findTicket(ticketId);
        if (target == MaintenanceAttachment.Phase.RESULT) {
            assertCanUploadResult(user, ticket);
            if (!acceptsResultFiles(ticket)) {
                throw new MaintenanceTicketException("Không đính kèm thêm được ảnh kết quả vào phiếu ở trạng thái "
                    + ticket.getStatus() + ".");
            }
        } else {
            assertCanUpload(user, ticket);
            if (ticket.isWorkFinished()) {
                throw new MaintenanceTicketException("Không đính kèm thêm được vào phiếu đã xử lý xong hoặc đã đóng");
            }
        }

        // Kiểm tra loại, số lượng và dung lượng của cả lượt trước khi ghi tệp nào vào kho
        // Ảnh kết quả tính theo đợt sửa: mỗi lần người thuê báo "Chưa đạt" mở một đợt mới với giới hạn riêng
        int round = target == MaintenanceAttachment.Phase.RESULT ? currentRound(ticketId) : 1;
        List<MaintenanceAttachment> existing = attachmentRepository.findByTicketId(ticketId).stream()
            .filter(attachment -> attachment.phase() == target && attachment.round() == round).toList();
        long images = existing.stream().filter(a -> a.kind() == MaintenanceAttachment.Kind.IMAGE).count();
        long videos = existing.stream().filter(a -> a.kind() == MaintenanceAttachment.Kind.VIDEO).count();
        List<FileSanitizer.SanitizedFile> sanitized = new ArrayList<>();
        List<MaintenanceAttachment.Kind> kinds = new ArrayList<>();
        for (AttachmentFile upload : files) {
            boolean video = FileSanitizer.isVideo(upload.contentType());
            if (!video && !FileSanitizer.isImage(upload.contentType())) {
                throw new IllegalArgumentException("Chỉ nhận ảnh jpeg/png/webp hoặc video mp4/webm/mov");
            }
            if (video && ++videos > 1) {
                throw new AttachmentLimitException("Phiếu chỉ được đính kèm một video");
            }
            if (!video && ++images > maxImages) {
                throw new AttachmentLimitException("Mỗi phiếu chỉ được đính kèm tối đa " + maxImages + " ảnh");
            }
            sanitized.add(video
                ? FileSanitizer.sanitizeVideo(upload.contentType(), upload.content(), maxVideoBytes)
                : FileSanitizer.sanitize(upload.contentType(), upload.content(), maxImageBytes));
            kinds.add(video ? MaintenanceAttachment.Kind.VIDEO : MaintenanceAttachment.Kind.IMAGE);
        }

        List<String> storedKeys = new ArrayList<>();
        List<MaintenanceAttachment> saved = new ArrayList<>();
        try {
            for (int i = 0; i < sanitized.size(); i++) {
                FileSanitizer.SanitizedFile file = sanitized.get(i);
                String key = PrivateFileStorageService.objectKey(ticket.getPropertyId(), "maintenance", file.extension());
                try {
                    storagePort.put(key, file.contentType(), file.content());
                } catch (RuntimeException ex) {
                    log.error("Không lưu được tệp phiếu bảo trì {} vào kho lưu trữ (key {})", ticketId, key, ex);
                    throw new FileStorageUnavailableException(
                        "Không lưu được tệp vào kho lưu trữ, vui lòng thử lại sau. Nếu lỗi lặp lại, hãy báo quản trị viên.", ex);
                }
                storedKeys.add(key);
                saved.add(attachmentRepository.save(new MaintenanceAttachment(UUID.randomUUID(), ticketId, kinds.get(i),
                    key, file.contentType(), file.content().length, user.id(), null, target, round)));
            }
        } catch (RuntimeException ex) {
            storedKeys.forEach(this::deleteQuietly);
            throw ex;
        }
        saved.forEach(attachment -> auditPort.recordEvent(AuditPort.AuditEvent.of("MAINTENANCE_ATTACHMENT_ADDED", user.id(),
            "MAINTENANCE_TICKET", ticketId.toString(), Map.of("kind", attachment.kind().name(), "phase", attachment.phase().name(), "size", attachment.sizeBytes()))));
        return List.copyOf(saved);
    }

    @Override
    public List<MaintenanceAttachment> list(UUID ticketId) {
        assertCanView(currentUserPort.getCurrentUser(), findTicket(ticketId));
        return attachmentRepository.findByTicketId(ticketId);
    }

    @Override
    public URI signedUrl(UUID ticketId, UUID attachmentId) {
        assertCanView(currentUserPort.getCurrentUser(), findTicket(ticketId));
        MaintenanceAttachment attachment = find(ticketId, attachmentId);
        try {
            return storagePort.createSignedUrl(attachment.storageKey(), SIGNED_URL_TTL);
        } catch (RuntimeException ex) {
            throw unavailable(attachment, ex);
        }
    }

    @Override
    public AttachmentContent content(UUID ticketId, UUID attachmentId) {
        assertCanView(currentUserPort.getCurrentUser(), findTicket(ticketId));
        MaintenanceAttachment attachment = find(ticketId, attachmentId);
        try {
            return new AttachmentContent(attachment.contentType(), storagePort.get(attachment.storageKey()));
        } catch (RuntimeException ex) {
            throw unavailable(attachment, ex);
        }
    }

    private static FileStorageUnavailableException unavailable(MaintenanceAttachment attachment, RuntimeException cause) {
        log.error("Không lấy được tệp phiếu bảo trì {} từ kho lưu trữ (key {})", attachment.id(), attachment.storageKey(), cause);
        return new FileStorageUnavailableException("Không mở được tệp từ kho lưu trữ, vui lòng thử lại sau.", cause);
    }

    @Override
    public void delete(UUID ticketId, UUID attachmentId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        MaintenanceTicket ticket = findTicket(ticketId);
        MaintenanceAttachment attachment = find(ticketId, attachmentId);
        boolean uploader = user != null && user.id().equals(attachment.uploadedBy());
        if (isTenantEvidence(ticket, attachment)) {
            // Ảnh/video người thuê gửi kèm phiếu là bằng chứng (#228): chỉ chính người thuê xóa, khi phiếu chưa xử lý xong
            if (!uploader) {
                if (user == null) {
                    throw new UnauthorizedPropertyAccessException(AuthorizationService.MSG_LOGIN_REQUIRED);
                }
                assertCanView(user, ticket);
                throw new UnauthorizedPropertyAccessException("Tệp do người thuê gửi chỉ người thuê được xóa.");
            }
            if (ticket.isWorkFinished()) {
                throw new MaintenanceTicketException("Không xóa được tệp của phiếu đã xử lý xong hoặc đã đóng");
            }
        } else if (!uploader) {
            authorizationService.assertCanManageProperty(user, ticket.getPropertyId());
        } else {
            assertCanView(user, ticket);
        }
        if (attachment.phase() == MaintenanceAttachment.Phase.RESULT && uploader && !acceptsResultFiles(ticket)) {
            throw new MaintenanceTicketException("Không xóa được ảnh kết quả của phiếu đã nghiệm thu hoặc đã đóng");
        }
        attachmentRepository.delete(attachmentId);
        deleteQuietly(attachment.storageKey());
        auditPort.recordEvent(AuditPort.AuditEvent.of("MAINTENANCE_ATTACHMENT_REMOVED", user.id(), "MAINTENANCE_TICKET",
            ticketId.toString(), Map.of("kind", attachment.kind().name())));
    }

    @Override
    public Set<UUID> deletableIds(UUID ticketId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        MaintenanceTicket ticket = findTicket(ticketId);
        assertCanView(user, ticket);
        Set<UUID> result = new HashSet<>();
        for (MaintenanceAttachment attachment : attachmentRepository.findByTicketId(ticketId)) {
            boolean uploader = user.id().equals(attachment.uploadedBy());
            if (isTenantEvidence(ticket, attachment)) {
                if (uploader && !ticket.isWorkFinished()) {
                    result.add(attachment.id());
                }
            } else if (user.role() == UserRole.TECHNICIAN) {
                // Kỹ thuật viên chỉ xóa ảnh kết quả do chính mình tải, khi người thuê chưa nghiệm thu
                if (uploader && attachment.phase() == MaintenanceAttachment.Phase.RESULT && acceptsResultFiles(ticket)) {
                    result.add(attachment.id());
                }
            } else if (uploader || user.role() != UserRole.TENANT) {
                result.add(attachment.id());
            }
        }
        return result;
    }

    /** Tệp do chính người thuê đã báo phiếu tải lên. */
    private boolean isTenantEvidence(MaintenanceTicket ticket, MaintenanceAttachment attachment) {
        if (ticket.getRequestedByTenantId() == null || attachment.uploadedBy() == null) {
            return false;
        }
        return tenantRepository.findById(ticket.getRequestedByTenantId())
            .map(tenant -> attachment.uploadedBy().equals(tenant.userId()))
            .orElse(false);
    }

    /** Đợt sửa hiện tại: 1 + số lần người thuê đã báo "Chưa đạt". */
    private int currentRound(UUID ticketId) {
        return 1 + (int) ticketRepository.findEvents(ticketId).stream()
            .filter(event -> event.note() != null && event.note().startsWith("Người thuê phản hồi chưa đạt"))
            .count();
    }

    /** Còn nhận ảnh kết quả: từ lúc giao việc tới khi người thuê nghiệm thu; sau đó (VERIFIED) hoặc phiếu đóng thì khóa. */
    private static boolean acceptsResultFiles(MaintenanceTicket ticket) {
        return switch (ticket.getStatus()) {
            case ASSIGNED, IN_PROGRESS, AWAITING_PAYMENT, RESOLVED -> true;
            default -> false;
        };
    }

    /** Ảnh kết quả: kỹ thuật viên được giao phiếu hoặc người quản lý nhà trọ; người thuê không được tải. */
    private void assertCanUploadResult(CurrentUser user, MaintenanceTicket ticket) {
        if (user == null) {
            throw new UnauthorizedPropertyAccessException(AuthorizationService.MSG_LOGIN_REQUIRED);
        }
        if (user.role() == UserRole.TENANT) {
            throw new UnauthorizedPropertyAccessException("Người thuê không tải được ảnh kết quả sửa chữa.");
        }
        if (user.role() == UserRole.TECHNICIAN) {
            assertCanView(user, ticket);
            return;
        }
        authorizationService.assertCanManageProperty(user, ticket.getPropertyId());
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
            throw new UnauthorizedPropertyAccessException(AuthorizationService.MSG_LOGIN_REQUIRED);
        }
        if (user.role() == UserRole.TENANT) {
            Tenant tenant = tenantRepository.findByUserId(user.id()).orElse(null);
            if (tenant == null || !authorizationService.canTenantViewTicket(user, ticket.getRoomId(),
                ticket.getRequestedByTenantId())) {
                throw new UnauthorizedPropertyAccessException("Người thuê chỉ xem được tệp đính kèm của phiếu thuộc phòng mình.");
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
            .orElseThrow(() -> new MaintenanceTicketException("Không tìm thấy phiếu bảo trì: " + ticketId));
    }

    private MaintenanceAttachment find(UUID ticketId, UUID attachmentId) {
        return attachmentRepository.findById(attachmentId)
            .filter(attachment -> attachment.ticketId().equals(ticketId))
            .orElseThrow(() -> new MaintenanceTicketException("Không tìm thấy tệp đính kèm: " + attachmentId));
    }

    private void deleteQuietly(String key) {
        try {
            storagePort.delete(key);
        } catch (RuntimeException ignored) {
            // best effort
        }
    }
}
