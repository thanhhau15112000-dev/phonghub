package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.FileStorageUseCase;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/properties/{propertyId}/files")
public class FileStorageApiController {

    private final FileStorageUseCase useCase;

    public FileStorageApiController(FileStorageUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<FileResponse> upload(
        @PathVariable UUID propertyId,
        @RequestParam String category,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        FileStorageUseCase.StoredFile stored = useCase.upload(new FileStorageUseCase.UploadFileCommand(
            propertyId,
            category,
            file.getOriginalFilename(),
            file.getContentType(),
            file.getBytes()
        ));
        return ResponseEntity.created(URI.create("/api/properties/" + propertyId + "/files/signed-url"))
            .body(new FileResponse(stored.objectKey(), stored.contentType(), stored.size()));
    }

    @GetMapping("/signed-url")
    public ResponseEntity<SignedUrlResponse> signedUrl(
        @PathVariable UUID propertyId,
        @RequestParam String objectKey,
        @RequestParam(defaultValue = "600") long ttlSeconds
    ) {
        URI signedUrl = useCase.createSignedUrl(propertyId, objectKey, Duration.ofSeconds(ttlSeconds));
        return ResponseEntity.ok(new SignedUrlResponse(signedUrl.toString()));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
        @PathVariable UUID propertyId,
        @RequestParam String objectKey
    ) {
        useCase.delete(propertyId, objectKey);
        return ResponseEntity.noContent().build();
    }

    public record FileResponse(String objectKey, String contentType, long size) {}

    public record SignedUrlResponse(String url) {}
}
