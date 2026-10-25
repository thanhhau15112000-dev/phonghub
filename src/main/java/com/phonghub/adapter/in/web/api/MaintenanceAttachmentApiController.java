package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.MaintenanceAttachmentUseCase;
import com.phonghub.domain.model.MaintenanceAttachment;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
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
@RequestMapping("/api/maintenance/{ticketId}/attachments")
public class MaintenanceAttachmentApiController {

    private final MaintenanceAttachmentUseCase useCase;

    public MaintenanceAttachmentApiController(MaintenanceAttachmentUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<AttachmentResponse> list(@PathVariable UUID ticketId) {
        return useCase.list(ticketId).stream().map(AttachmentResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<?> upload(
        @PathVariable UUID ticketId,
        @RequestParam(name = "file", required = false) MultipartFile file,
        @RequestParam(name = "files", required = false) List<MultipartFile> files
    ) throws IOException {
        List<MultipartFile> uploads = files == null ? new ArrayList<>() : new ArrayList<>(files);
        if (file != null && !file.isEmpty()) uploads.add(file);
        if (uploads.stream().noneMatch(upload -> upload != null && !upload.isEmpty())) {
            throw new IllegalArgumentException("Cần chọn ít nhất một tệp để tải lên.");
        }
        List<AttachmentResponse> saved = new ArrayList<>();
        for (MultipartFile upload : uploads) {
            if (upload != null && !upload.isEmpty()) {
                saved.add(AttachmentResponse.from(useCase.upload(ticketId, upload.getContentType(), upload.getBytes())));
            }
        }
        Object body = file != null && files == null && saved.size() == 1 ? saved.getFirst() : List.copyOf(saved);
        URI location = file != null && files == null && saved.size() == 1
            ? URI.create("/api/maintenance/" + ticketId + "/attachments/" + saved.getFirst().id())
            : URI.create("/api/maintenance/" + ticketId + "/attachments");
        return ResponseEntity.created(location)
            .body(body);
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<Void> download(@PathVariable UUID ticketId, @PathVariable UUID attachmentId) {
        URI target = useCase.signedUrl(ticketId, attachmentId);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, target.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID ticketId, @PathVariable UUID attachmentId) {
        useCase.delete(ticketId, attachmentId);
        return ResponseEntity.noContent().build();
    }

    /** Không trả khóa tệp trong kho; tải qua đường dẫn có chữ ký của chính endpoint này. */
    public record AttachmentResponse(UUID id, String kind, String contentType, long sizeBytes, UUID uploadedBy,
                                     Instant createdAt) {
        static AttachmentResponse from(MaintenanceAttachment attachment) {
            return new AttachmentResponse(attachment.id(), attachment.kind().name(), attachment.contentType(),
                attachment.sizeBytes(), attachment.uploadedBy(), attachment.createdAt());
        }
    }
}
