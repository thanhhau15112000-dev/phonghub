package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phonghub.application.port.in.MaintenanceAttachmentUseCase.AttachmentFile;
import com.phonghub.application.port.out.AuditPort;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.port.out.MaintenanceAttachmentRepositoryPort;
import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.MaintenanceAttachmentService;
import com.phonghub.domain.exception.MaintenanceTicketException;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.LiableParty;
import com.phonghub.domain.model.MaintenanceAttachment;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #232: ảnh/video kết quả sửa chữa (RESULT) tách khỏi ảnh báo hỏng (REPORT). */
class MaintenanceResultAttachmentUnitTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    private static final byte[] MP4 = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2', 0, 0, 0, 0, 'm', 'p', '4', '2'};

    private final CurrentUser technician = new CurrentUser(UUID.randomUUID(), "t@x.vn", "Tech", UserRole.TECHNICIAN);
    private final CurrentUser otherTechnician = new CurrentUser(UUID.randomUUID(), "t2@x.vn", "Tech 2", UserRole.TECHNICIAN);
    private final CurrentUser tenant = new CurrentUser(UUID.randomUUID(), "u@x.vn", "Tenant", UserRole.TENANT);

    private MaintenanceAttachmentRepositoryPort attachments;
    private FileStoragePort storage;
    private CurrentUserPort currentUserPort;
    private MaintenanceTicketRepositoryPort tickets;
    private MaintenanceAttachmentService service;

    @BeforeEach
    void setUp() {
        attachments = mock(MaintenanceAttachmentRepositoryPort.class);
        storage = mock(FileStoragePort.class);
        tickets = mock(MaintenanceTicketRepositoryPort.class);
        currentUserPort = mock(CurrentUserPort.class);
        service = new MaintenanceAttachmentService(attachments, tickets, mock(TenantRepositoryPort.class), storage,
            currentUserPort, mock(AuthorizationService.class), mock(AuditPort.class), 5, 10_485_760L, 10_485_760L);
        when(attachments.findByTicketId(any())).thenReturn(List.of());
        when(attachments.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private MaintenanceTicket ticket(MaintenanceStatus status) {
        MaintenanceTicket ticket = new MaintenanceTicket(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
            technician.id(), "Vòi nước rỉ", "Bồn rửa", MaintenancePriority.MEDIUM, status, BigDecimal.ZERO, null, null,
            LiableParty.OWNER, null, null, null, Instant.now(), Instant.now());
        when(tickets.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        return ticket;
    }

    private static List<AttachmentFile> oneImageAndOneVideo() {
        return List.of(new AttachmentFile("image/png", PNG), new AttachmentFile("video/mp4", MP4));
    }

    @Test
    @DisplayName("Kỹ thuật viên được giao tải ảnh và video kết quả khi phiếu đang sửa, lưu với loại RESULT")
    void assignedTechnicianUploadsResultWhileInProgress() {
        MaintenanceTicket ticket = ticket(MaintenanceStatus.IN_PROGRESS);
        when(currentUserPort.getCurrentUser()).thenReturn(technician);

        List<MaintenanceAttachment> saved = service.uploadAll(ticket.getId(), oneImageAndOneVideo(),
            MaintenanceAttachment.Phase.RESULT);

        assertEquals(2, saved.size());
        assertTrue(saved.stream().allMatch(a -> a.phase() == MaintenanceAttachment.Phase.RESULT));
    }

    @Test
    @DisplayName("Kỹ thuật viên không được giao phiếu thì bị chặn (403)")
    void unassignedTechnicianIsRefused() {
        MaintenanceTicket ticket = ticket(MaintenanceStatus.IN_PROGRESS);
        when(currentUserPort.getCurrentUser()).thenReturn(otherTechnician);

        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.uploadAll(ticket.getId(),
            List.of(new AttachmentFile("image/png", PNG)), MaintenanceAttachment.Phase.RESULT));
        verify(storage, never()).put(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Người thuê không tải được ảnh kết quả (403)")
    void tenantCannotUploadResult() {
        MaintenanceTicket ticket = ticket(MaintenanceStatus.IN_PROGRESS);
        when(currentUserPort.getCurrentUser()).thenReturn(tenant);

        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.uploadAll(ticket.getId(),
            List.of(new AttachmentFile("image/png", PNG)), MaintenanceAttachment.Phase.RESULT));
    }

    @Test
    @DisplayName("Kỹ thuật viên không tải được ảnh báo hỏng (REPORT)")
    void technicianCannotUploadReportPhoto() {
        MaintenanceTicket ticket = ticket(MaintenanceStatus.IN_PROGRESS);
        when(currentUserPort.getCurrentUser()).thenReturn(technician);
        // AuthorizationService là mock không ném lỗi; quyền REPORT của kỹ thuật viên được kiểm ở assertCanManageProperty.
        org.mockito.Mockito.doThrow(new UnauthorizedPropertyAccessException("no"))
            .when(authorization()).assertCanManageProperty(any(), any());

        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.uploadAll(ticket.getId(),
            List.of(new AttachmentFile("image/png", PNG))));
    }

    @Test
    @DisplayName("Phiếu RESOLVED mà người thuê chưa nghiệm thu: kỹ thuật viên vẫn bổ sung được ảnh kết quả")
    void technicianCanAddAfterResolvedBeforeVerification() {
        MaintenanceTicket ticket = ticket(MaintenanceStatus.RESOLVED);
        when(currentUserPort.getCurrentUser()).thenReturn(technician);

        List<MaintenanceAttachment> saved = service.uploadAll(ticket.getId(),
            List.of(new AttachmentFile("image/png", PNG)), MaintenanceAttachment.Phase.RESULT);

        assertEquals(1, saved.size());
    }

    @Test
    @DisplayName("Sau khi người thuê nghiệm thu (VERIFIED) thì khóa, không tải thêm ảnh kết quả (409)")
    void lockedAfterVerification() {
        MaintenanceTicket ticket = ticket(MaintenanceStatus.VERIFIED);
        when(currentUserPort.getCurrentUser()).thenReturn(technician);

        assertThrows(MaintenanceTicketException.class, () -> service.uploadAll(ticket.getId(),
            List.of(new AttachmentFile("image/png", PNG)), MaintenanceAttachment.Phase.RESULT));
        verify(storage, never()).put(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Giới hạn một video tính riêng cho từng loại: đã có video báo hỏng vẫn tải được video kết quả")
    void videoLimitIsPerPhase() {
        MaintenanceTicket ticket = ticket(MaintenanceStatus.IN_PROGRESS);
        when(currentUserPort.getCurrentUser()).thenReturn(technician);
        when(attachments.findByTicketId(ticket.getId())).thenReturn(List.of(new MaintenanceAttachment(UUID.randomUUID(),
            ticket.getId(), MaintenanceAttachment.Kind.VIDEO, "maintenance/report-video", "video/mp4", 20, tenant.id(),
            null, MaintenanceAttachment.Phase.REPORT)));

        List<MaintenanceAttachment> saved = service.uploadAll(ticket.getId(),
            List.of(new AttachmentFile("video/mp4", MP4)), MaintenanceAttachment.Phase.RESULT);

        assertEquals(1, saved.size());
    }

    @Test
    @DisplayName("Sau mỗi lần người thuê báo chưa đạt, ảnh kết quả mới thuộc đợt kế tiếp và có giới hạn riêng")
    void resultPhotosAreNumberedByRound() {
        MaintenanceTicket ticket = ticket(MaintenanceStatus.RESOLVED);
        when(currentUserPort.getCurrentUser()).thenReturn(technician);
        MaintenanceAttachment round1Video = new MaintenanceAttachment(UUID.randomUUID(), ticket.getId(),
            MaintenanceAttachment.Kind.VIDEO, "k", "video/mp4", 20, technician.id(), null,
            MaintenanceAttachment.Phase.RESULT, 1);
        when(attachments.findByTicketId(ticket.getId())).thenReturn(List.of(round1Video));

        // chưa có phản hồi chưa đạt: vẫn đợt 1, video thứ hai bị chặn
        assertThrows(com.phonghub.domain.exception.AttachmentLimitException.class, () -> service.uploadAll(ticket.getId(),
            List.of(new AttachmentFile("video/mp4", MP4)), MaintenanceAttachment.Phase.RESULT));

        ticket.reopenByTenant("vẫn rỉ nước");
        when(tickets.findEvents(ticket.getId())).thenReturn(ticket.drainEvents());
        List<MaintenanceAttachment> saved = service.uploadAll(ticket.getId(),
            List.of(new AttachmentFile("video/mp4", MP4)), MaintenanceAttachment.Phase.RESULT);

        assertEquals(2, saved.getFirst().round());
    }

    @Test
    @DisplayName("Kỹ thuật viên chỉ xóa được ảnh kết quả do chính mình tải, và chỉ khi chưa nghiệm thu")
    void technicianDeletesOnlyOwnResultPhotos() {
        MaintenanceTicket open = ticket(MaintenanceStatus.RESOLVED);
        when(currentUserPort.getCurrentUser()).thenReturn(technician);
        MaintenanceAttachment own = new MaintenanceAttachment(UUID.randomUUID(), open.getId(),
            MaintenanceAttachment.Kind.IMAGE, "k1", "image/png", 10, technician.id(), null, MaintenanceAttachment.Phase.RESULT);
        MaintenanceAttachment report = new MaintenanceAttachment(UUID.randomUUID(), open.getId(),
            MaintenanceAttachment.Kind.IMAGE, "k2", "image/png", 10, UUID.randomUUID(), null,
            MaintenanceAttachment.Phase.REPORT);
        when(attachments.findByTicketId(open.getId())).thenReturn(List.of(own, report));

        assertTrue(service.deletableIds(open.getId()).contains(own.id()));
        assertFalse(service.deletableIds(open.getId()).contains(report.id()));
    }

    // AuthorizationService được dùng chung với setUp; tạo lại service với mock có thể cấu hình.
    private AuthorizationService authorization;

    private AuthorizationService authorization() {
        authorization = mock(AuthorizationService.class);
        service = new MaintenanceAttachmentService(attachments, tickets, mock(TenantRepositoryPort.class), storage,
            currentUserPort, authorization, mock(AuditPort.class), 5, 10_485_760L, 10_485_760L);
        return authorization;
    }
}
