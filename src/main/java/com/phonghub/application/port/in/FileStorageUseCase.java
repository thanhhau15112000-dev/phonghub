package com.phonghub.application.port.in;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

public interface FileStorageUseCase {

    StoredFile upload(UploadFileCommand command);

    URI createSignedUrl(UUID propertyId, String objectKey, Duration requestedTtl);

    void delete(UUID propertyId, String objectKey);

    record UploadFileCommand(
        UUID propertyId,
        String category,
        String originalFilename,
        String contentType,
        byte[] content
    ) {}

    record StoredFile(String objectKey, String contentType, long size) {}
}
