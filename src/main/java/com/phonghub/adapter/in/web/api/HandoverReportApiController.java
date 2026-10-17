package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.HandoverReportUseCase;
import com.phonghub.application.port.in.HandoverReportUseCase.Comparison;
import com.phonghub.application.port.in.HandoverReportUseCase.PdfDocument;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.Handover;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contracts/{contractId}")
public class HandoverReportApiController {

    private final HandoverReportUseCase useCase;

    public HandoverReportApiController(HandoverReportUseCase useCase) {
        this.useCase = useCase;
    }

    /** Đối chiếu tài sản và chỉ số điện nước giữa check-in và check-out. */
    @GetMapping("/handover-comparison")
    public ComparisonResponse compare(@PathVariable UUID contractId) {
        return ComparisonResponse.from(useCase.compare(contractId));
    }

    @GetMapping("/handover-report/{kind:check-in|check-out}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID contractId, @PathVariable String kind) {
        HandoverType type = "check-out".equals(kind) ? HandoverType.CHECK_OUT : HandoverType.CHECK_IN;
        PdfDocument document = useCase.exportPdf(contractId, type);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(document.filename()).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(document.content());
    }

    /** Không trả khóa tệp trong kho hay mã băm; ảnh xem qua đường dẫn ảnh có chữ ký của biên bản. */
    public record ComparisonResponse(
        Summary checkIn,
        Summary checkOut,
        List<HandoverReportUseCase.ItemRow> rows,
        List<UUID> checkInPhotoIds,
        List<UUID> checkOutPhotoIds,
        BigDecimal electricConsumption,
        BigDecimal waterConsumption
    ) {
        static ComparisonResponse from(Comparison comparison) {
            return new ComparisonResponse(Summary.from(comparison.checkIn()), Summary.from(comparison.checkOut()),
                comparison.rows(),
                comparison.checkInPhotos().stream().map(photo -> photo.id()).toList(),
                comparison.checkOutPhotos().stream().map(photo -> photo.id()).toList(),
                comparison.electricConsumption(), comparison.waterConsumption());
        }
    }

    public record Summary(String status, BigDecimal electricMeter, BigDecimal waterMeter, String roomCondition,
                          Instant sentAt, Instant confirmedAt, Instant approvedAt, BigDecimal finalDeduction) {
        static Summary from(Handover handover) {
            if (handover == null) {
                return null;
            }
            return new Summary(handover.status().name(), handover.initialElectricMeter(),
                handover.initialWaterMeter(), handover.roomCondition(), handover.sentAt(), handover.confirmedAt(),
                handover.approvedAt(), handover.finalDeduction());
        }
    }
}
