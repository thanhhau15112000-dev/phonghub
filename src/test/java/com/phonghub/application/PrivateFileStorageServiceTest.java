package com.phonghub.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.phonghub.application.port.in.FileStorageUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.application.service.PrivateFileStorageService;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.UserRole;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PrivateFileStorageServiceTest {

    private final FileStoragePort storagePort = mock(FileStoragePort.class);
    private final CurrentUserPort currentUserPort = mock(CurrentUserPort.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final CurrentUser owner = new CurrentUser(
        UUID.randomUUID(), "owner@local", "Owner", UserRole.OWNER
    );
    private PrivateFileStorageService service;

    @BeforeEach
    void setUp() {
        when(currentUserPort.getCurrentUser()).thenReturn(owner);
        service = new PrivateFileStorageService(storagePort, currentUserPort, authorizationService, 16);
    }

    @Test
    void uploadsAllowedFileUnderRandomPropertyScopedKey() {
        UUID propertyId = UUID.randomUUID();
        byte[] pdf = "%PDF-1.7\nbody".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        FileStorageUseCase.StoredFile result = service.upload(new FileStorageUseCase.UploadFileCommand(
            propertyId, "documents", "cccd.pdf", "application/pdf", pdf
        ));

        assertTrue(result.objectKey().matches(
            "properties/" + propertyId + "/documents/[0-9a-f-]{36}\\.pdf"
        ));
        assertEquals("application/pdf", result.contentType());
        verify(authorizationService).assertCanManageProperty(owner, propertyId);
        verify(storagePort).put(result.objectKey(), "application/pdf", pdf);
    }

    @Test
    void rejectsSpoofedContentTypeAndOversizedPayload() {
        UUID propertyId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> service.upload(
            new FileStorageUseCase.UploadFileCommand(
                propertyId, "documents", "fake.png", "image/png", "%PDF-1.7".getBytes()
            )
        ));
        assertThrows(IllegalArgumentException.class, () -> service.upload(
            new FileStorageUseCase.UploadFileCommand(
                propertyId, "documents", "large.pdf", "application/pdf", new byte[17]
            )
        ));
    }

    @Test
    void blocksAnotherPropertyBeforeStorageAccess() {
        UUID propertyId = UUID.randomUUID();
        doThrow(new UnauthorizedPropertyAccessException("forbidden"))
            .when(authorizationService).assertCanManageProperty(owner, propertyId);

        assertThrows(UnauthorizedPropertyAccessException.class, () -> service.upload(
            new FileStorageUseCase.UploadFileCommand(
                propertyId,
                "documents",
                "cccd.pdf",
                "application/pdf",
                "%PDF-1.7".getBytes()
            )
        ));
    }

    @Test
    void capsSignedUrlAtTenMinutesAndRejectsForeignObjectKey() {
        UUID propertyId = UUID.randomUUID();
        String key = "properties/" + propertyId + "/documents/file.pdf";
        when(storagePort.createSignedUrl(any(), any())).thenReturn(URI.create("https://signed.local/file"));

        service.createSignedUrl(propertyId, key, Duration.ofHours(1));

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(storagePort).createSignedUrl(org.mockito.ArgumentMatchers.eq(key), ttl.capture());
        assertEquals(Duration.ofMinutes(10), ttl.getValue());
        assertThrows(IllegalArgumentException.class, () -> service.createSignedUrl(
            propertyId,
            "properties/" + UUID.randomUUID() + "/documents/file.pdf",
            Duration.ofMinutes(1)
        ));
    }
}
