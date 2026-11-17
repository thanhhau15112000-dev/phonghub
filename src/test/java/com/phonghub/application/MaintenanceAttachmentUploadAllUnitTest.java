package com.phonghub.application;

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
import com.phonghub.domain.exception.AttachmentLimitException;
import com.phonghub.domain.model.MaintenanceAttachment;
import com.phonghub.domain.model.MaintenancePriority;
import com.phonghub.domain.model.MaintenanceTicket;
import com.phonghub.domain.model.UserRole;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tải nhiều tệp một lượt: kiểm tra cả lượt trước khi ghi, lỗi giữa chừng thì dọn tệp đã ghi vào kho. */
class MaintenanceAttachmentUploadAllUnitTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    private static final byte[] MP4 = {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2', 0, 0, 0, 0, 'm', 'p', '4', '2'};

    private final CurrentUser owner = new CurrentUser(UUID.randomUUID(), "o@x.vn", "Owner", UserRole.OWNER);

    private MaintenanceAttachmentRepositoryPort attachments;
    private FileStoragePort storage;
    private MaintenanceAttachmentService service;
    private MaintenanceTicket ticket;

    @BeforeEach
    void setUp() {
        attachments = mock(MaintenanceAttachmentRepositoryPort.class);
        storage = mock(FileStoragePort.class);
        MaintenanceTicketRepositoryPort tickets = mock(MaintenanceTicketRepositoryPort.class);
        CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
        service = new MaintenanceAttachmentService(attachments, tickets, mock(TenantRepositoryPort.class), storage,
            currentUserPort, mock(AuthorizationService.class), mock(AuditPort.class), 5, 10_485_760L, 10_485_760L);

        ticket = MaintenanceTicket.create(UUID.randomUUID(), UUID.randomUUID(), null, "Rò nước", "Vòi rò",
            MaintenancePriority.MEDIUM);
        when(tickets.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(currentUserPort.getCurrentUser()).thenReturn(owner);
        when(attachments.findByTicketId(ticket.getId())).thenReturn(List.of());
        when(attachments.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static List<AttachmentFile> images(int count) {
        return new ArrayList<>(Collections.nCopies(count, new AttachmentFile("image/png", PNG)));
    }

    private MaintenanceAttachment existingImage() {
        return new MaintenanceAttachment(UUID.randomUUID(), ticket.getId(), MaintenanceAttachment.Kind.IMAGE,
            "maintenance/" + UUID.randomUUID(), "image/png", PNG.length, owner.id(), null);
    }

    @Test
    @DisplayName("Six photos in one batch are refused before anything is written to storage")
    void sixImagesInOneBatchWriteNothing() {
        assertThrows(AttachmentLimitException.class, () -> service.uploadAll(ticket.getId(), images(6)));

        verify(storage, never()).put(anyString(), anyString(), any());
        verify(attachments, never()).save(any());
    }

    @Test
    @DisplayName("The photo limit counts attachments already on the ticket")
    void limitCountsExistingAttachments() {
        when(attachments.findByTicketId(ticket.getId()))
            .thenReturn(List.of(existingImage(), existingImage(), existingImage(), existingImage()));

        assertThrows(AttachmentLimitException.class, () -> service.uploadAll(ticket.getId(), images(2)));

        verify(storage, never()).put(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("An invalid file anywhere in the batch stops the whole batch before writing")
    void invalidFileStopsWholeBatch() {
        List<AttachmentFile> files = images(2);
        files.add(new AttachmentFile("image/png", "not an image".getBytes()));

        assertThrows(IllegalArgumentException.class, () -> service.uploadAll(ticket.getId(), files));

        verify(storage, never()).put(anyString(), anyString(), any());
        verify(attachments, never()).save(any());
    }

    @Test
    @DisplayName("When storage fails midway, files already written are deleted again")
    void storageFailureCleansUpWrittenFiles() {
        doNothing().doThrow(new IllegalStateException("storage down"))
            .when(storage).put(anyString(), anyString(), any());

        assertThrows(com.phonghub.domain.exception.FileStorageUnavailableException.class,
            () -> service.uploadAll(ticket.getId(), images(3)));

        ArgumentCaptor<String> putKeys = ArgumentCaptor.forClass(String.class);
        verify(storage, times(2)).put(putKeys.capture(), anyString(), any());
        verify(storage).delete(putKeys.getAllValues().getFirst());
        verify(storage, times(1)).delete(anyString());
    }

    @Test
    @DisplayName("Five photos and one video in a single batch are all stored")
    void validBatchStoresEverything() {
        List<AttachmentFile> files = images(5);
        files.add(new AttachmentFile("video/mp4", MP4));

        List<MaintenanceAttachment> saved = service.uploadAll(ticket.getId(), files);

        assertEquals(6, saved.size());
        assertEquals(1, saved.stream().filter(a -> a.kind() == MaintenanceAttachment.Kind.VIDEO).count());
        verify(storage, times(6)).put(anyString(), anyString(), any());
        verify(storage, never()).delete(anyString());
    }
}
