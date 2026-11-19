package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.TenantDocumentUseCase;
import com.phonghub.domain.model.DocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/properties/{propertyId}/tenants/{tenantId}/documents")
public class TenantDocumentApiController {

    private final TenantDocumentUseCase useCase;

    public TenantDocumentApiController(TenantDocumentUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public ResponseEntity<List<SummaryResponse>> list(@PathVariable UUID propertyId, @PathVariable UUID tenantId) {
        return noStore(useCase.list(propertyId, tenantId).stream().map(SummaryResponse::from).toList());
    }

    @GetMapping("/{type}")
    public ResponseEntity<SummaryResponse> view(
        @PathVariable UUID propertyId,
        @PathVariable UUID tenantId,
        @PathVariable DocumentType type
    ) {
        return noStore(SummaryResponse.from(useCase.view(propertyId, tenantId, type)));
    }

    @PutMapping("/{type}")
    public ResponseEntity<SummaryResponse> upload(
        @PathVariable UUID propertyId,
        @PathVariable UUID tenantId,
        @PathVariable DocumentType type,
        @RequestParam(required = false) String number,
        @RequestParam("front") MultipartFile front,
        @RequestParam(value = "back", required = false) MultipartFile back
    ) throws IOException {
        TenantDocumentUseCase.DocumentSummary summary = useCase.upload(new TenantDocumentUseCase.UploadDocumentCommand(
            propertyId, tenantId, type, number, toFile(front), toFile(back)));
        return noStore(SummaryResponse.from(summary));
    }

    @PostMapping("/{type}/reveal")
    public ResponseEntity<RevealResponse> reveal(
        @PathVariable UUID propertyId,
        @PathVariable UUID tenantId,
        @PathVariable DocumentType type,
        @Valid @RequestBody RevealRequest request
    ) {
        return noStore(RevealResponse.from(useCase.reveal(propertyId, tenantId, type, request.password())));
    }

    @DeleteMapping("/{type}")
    public ResponseEntity<Void> delete(
        @PathVariable UUID propertyId,
        @PathVariable UUID tenantId,
        @PathVariable DocumentType type
    ) {
        useCase.delete(propertyId, tenantId, type);
        return ResponseEntity.noContent().build();
    }

    private static TenantDocumentUseCase.DocumentFile toFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        return new TenantDocumentUseCase.DocumentFile(file.getContentType(), file.getBytes());
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    public record RevealRequest(@NotBlank String password) {
        @Override
        public String toString() {
            return "RevealRequest[password=***]";
        }
    }

    public record SummaryResponse(
        String type,
        String maskedNumber,
        boolean hasFrontImage,
        boolean hasBackImage,
        Instant updatedAt
    ) {
        static SummaryResponse from(TenantDocumentUseCase.DocumentSummary summary) {
            return new SummaryResponse(summary.type().name(), summary.maskedNumber(), summary.hasFrontImage(),
                summary.hasBackImage(), summary.updatedAt());
        }
    }

    public record ImageResponse(String contentType, String base64) {
        static ImageResponse from(TenantDocumentUseCase.DocumentFile file) {
            return file == null ? null
                : new ImageResponse(file.contentType(), Base64.getEncoder().encodeToString(file.content()));
        }
    }

    public record RevealResponse(String type, String number, ImageResponse front, ImageResponse back) {
        static RevealResponse from(TenantDocumentUseCase.RevealedDocument document) {
            return new RevealResponse(document.type().name(), document.number(),
                ImageResponse.from(document.front()), ImageResponse.from(document.back()));
        }
    }
}
