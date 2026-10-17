package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.MeterSubmissionUseCase;
import com.phonghub.domain.model.MeterSubmission;
import com.phonghub.domain.model.MeterType;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class MeterSubmissionApiController {

    private final MeterSubmissionUseCase useCase;

    public MeterSubmissionApiController(MeterSubmissionUseCase useCase) {
        this.useCase = useCase;
    }

    /** Người thuê gửi ảnh đồng hồ kèm số đã đọc được. */
    @PostMapping("/rooms/{roomId}/meter-submissions")
    public ResponseEntity<SubmissionResponse> submit(
        @PathVariable UUID roomId,
        @RequestParam("meterType") MeterType meterType,
        @RequestParam("declaredValue") BigDecimal declaredValue,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        MeterSubmission saved = useCase.submit(roomId, meterType, declaredValue, file.getContentType(), file.getBytes());
        return ResponseEntity.created(URI.create("/api/rooms/" + roomId + "/meter-submissions"))
            .body(SubmissionResponse.from(saved));
    }

    @GetMapping("/rooms/{roomId}/meter-submissions")
    public List<SubmissionResponse> listForRoom(@PathVariable UUID roomId) {
        return useCase.listForRoom(roomId).stream().map(SubmissionResponse::from).toList();
    }

    @GetMapping("/properties/{propertyId}/meter-submissions")
    public List<SubmissionResponse> listForProperty(
        @PathVariable UUID propertyId,
        @RequestParam(required = false) MeterSubmission.Status status
    ) {
        return useCase.listForProperty(propertyId, status).stream().map(SubmissionResponse::from).toList();
    }

    @PostMapping("/meter-submissions/{submissionId}/accept")
    public UtilityApiController.ReadingResponse accept(
        @PathVariable UUID submissionId,
        @RequestBody(required = false) AcceptRequest request
    ) {
        AcceptRequest body = request == null ? new AcceptRequest(null, null, null) : request;
        return UtilityApiController.ReadingResponse.from(useCase.accept(submissionId,
            new MeterSubmissionUseCase.AcceptCommand(body.finalValue(), body.baselineValue(),
                Boolean.TRUE.equals(body.acknowledgeMismatch()))));
    }

    @PostMapping("/meter-submissions/{submissionId}/reject")
    public SubmissionResponse reject(@PathVariable UUID submissionId, @RequestBody RejectRequest request) {
        return SubmissionResponse.from(useCase.reject(submissionId, request.reason()));
    }

    @GetMapping("/meter-submissions/{submissionId}/photo")
    public ResponseEntity<Void> photo(@PathVariable UUID submissionId) {
        URI target = useCase.photoSignedUrl(submissionId);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, target.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    public record AcceptRequest(BigDecimal finalValue, BigDecimal baselineValue, Boolean acknowledgeMismatch) {}

    public record RejectRequest(@NotBlank(message = "Cần nêu lý do từ chối") String reason) {}

    /** Không trả khóa tệp; ảnh xem qua endpoint ảnh có chữ ký. */
    public record SubmissionResponse(UUID id, UUID roomId, String meterType, BigDecimal declaredValue,
                                     BigDecimal ocrValue, BigDecimal ocrConfidence, String matchStatus, String status,
                                     BigDecimal finalValue, String reviewNote, Instant submittedAt, Instant reviewedAt) {
        static SubmissionResponse from(MeterSubmission s) {
            return new SubmissionResponse(s.id(), s.roomId(), s.meterType().name(), s.declaredValue(), s.ocrValue(),
                s.ocrConfidence(), s.matchStatus().name(), s.status().name(), s.finalValue(), s.reviewNote(),
                s.submittedAt(), s.reviewedAt());
        }
    }
}
