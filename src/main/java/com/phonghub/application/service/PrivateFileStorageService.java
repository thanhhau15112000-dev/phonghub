package com.phonghub.application.service;

import com.phonghub.application.port.in.FileStorageUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.port.out.FileStoragePort;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

public class PrivateFileStorageService implements FileStorageUseCase {

    private static final Duration MAX_SIGNED_URL_TTL = Duration.ofMinutes(10);

    private final FileStoragePort storagePort;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;
    private final long maxFileSizeBytes;

    public PrivateFileStorageService(
        FileStoragePort storagePort,
        CurrentUserPort currentUserPort,
        AuthorizationService authorizationService,
        long maxFileSizeBytes
    ) {
        this.storagePort = storagePort;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("Maximum file size must be positive");
        }
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public StoredFile upload(UploadFileCommand command) {
        if (command == null || command.propertyId() == null) {
            throw new IllegalArgumentException("Property is required");
        }
        CurrentUser currentUser = currentUserPort.getCurrentUser();
        authorizationService.assertCanManageProperty(currentUser, command.propertyId());

        FileSanitizer.SanitizedFile file = FileSanitizer.sanitize(
            command.contentType(), command.content(), maxFileSizeBytes);
        String objectKey = objectKey(command.propertyId(), command.category(), file.extension());
        storagePort.put(objectKey, file.contentType(), file.content());
        return new StoredFile(objectKey, file.contentType(), file.content().length);
    }

    @Override
    public URI createSignedUrl(UUID propertyId, String objectKey, Duration requestedTtl) {
        assertObjectAccess(propertyId, objectKey);
        Duration ttl = requestedTtl == null || requestedTtl.isNegative() || requestedTtl.isZero()
            ? MAX_SIGNED_URL_TTL
            : requestedTtl.compareTo(MAX_SIGNED_URL_TTL) > 0 ? MAX_SIGNED_URL_TTL : requestedTtl;
        return storagePort.createSignedUrl(objectKey, ttl);
    }

    @Override
    public void delete(UUID propertyId, String objectKey) {
        assertObjectAccess(propertyId, objectKey);
        storagePort.delete(objectKey);
    }

    /** Khóa đối tượng ngẫu nhiên nằm dưới phạm vi nhà trọ; dùng chung cho tệp thường và tệp đã mã hóa. */
    public static String objectKey(UUID propertyId, String category, String extension) {
        return "properties/" + propertyId + "/" + normalizeCategory(category) + "/"
            + UUID.randomUUID() + "." + extension;
    }

    private void assertObjectAccess(UUID propertyId, String objectKey) {
        if (propertyId == null || objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Property and object key are required");
        }
        authorizationService.assertCanManageProperty(currentUserPort.getCurrentUser(), propertyId);
        String expectedPrefix = "properties/" + propertyId + "/";
        if (!objectKey.startsWith(expectedPrefix) || objectKey.contains("..")) {
            throw new IllegalArgumentException("Object key does not belong to the property");
        }
    }

    private static String normalizeCategory(String category) {
        if (category == null || !category.matches("[a-z0-9][a-z0-9-]{0,39}")) {
            throw new IllegalArgumentException("Invalid storage category");
        }
        return category;
    }
}
