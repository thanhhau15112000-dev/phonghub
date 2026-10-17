package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.UtilityUseCase;
import com.phonghub.domain.model.ChargeType;
import com.phonghub.domain.model.MeterReading;
import com.phonghub.domain.model.MeterType;
import com.phonghub.domain.model.ServiceRate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class UtilityApiController {

    private final UtilityUseCase useCase;

    public UtilityApiController(UtilityUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/rooms/{roomId}/meter-readings")
    public List<ReadingResponse> list(@PathVariable UUID roomId) {
        return useCase.listReadings(roomId).stream().map(ReadingResponse::from).toList();
    }

    @PostMapping("/rooms/{roomId}/meter-readings")
    public ResponseEntity<ReadingResponse> record(
        @PathVariable UUID roomId,
        @Valid @RequestBody ReadingRequest request
    ) {
        UtilityUseCase.Replacement replacement = request.replacement() == null ? null
            : new UtilityUseCase.Replacement(request.replacement().oldFinalValue(),
                request.replacement().newStartValue(), request.replacement().note());
        MeterReading reading = useCase.recordReading(new UtilityUseCase.RecordReadingCommand(roomId,
            request.meterType(), request.currentValue(), request.readingDate(), request.baselineValue(), replacement, request.ocrValue(), request.ocrConfidence()));
        return ResponseEntity.created(URI.create("/api/rooms/" + roomId + "/meter-readings"))
            .body(ReadingResponse.from(reading));
    }

    @PostMapping("/rooms/{roomId}/meter-readings/ocr")
    public OcrResponse readPhoto(
        @PathVariable UUID roomId,
        @RequestParam("meterType") MeterType meterType,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        UtilityUseCase.OcrSuggestion suggestion = useCase.readMeterPhoto(roomId, meterType, file.getContentType(),
            file.getBytes());
        return new OcrResponse(suggestion.value(), suggestion.confidence(), suggestion.requiresManualEntry(),
            suggestion.engine());
    }

    @PostMapping("/meter-readings/{readingId}/photo")
    public ReadingResponse attachPhoto(
        @PathVariable UUID readingId,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        return ReadingResponse.from(useCase.attachPhoto(readingId, file.getContentType(), file.getBytes()));
    }

    @GetMapping("/meter-readings/{readingId}/photo")
    public ResponseEntity<Void> photo(@PathVariable UUID readingId) {
        URI target = useCase.photoSignedUrl(readingId);
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, target.toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @GetMapping("/properties/{propertyId}/service-rates")
    public List<RateResponse> listRates(@PathVariable UUID propertyId) {
        return useCase.listRates(propertyId).stream().map(RateResponse::from).toList();
    }

    @PostMapping("/properties/{propertyId}/service-rates")
    public ResponseEntity<RateResponse> createRate(
        @PathVariable UUID propertyId,
        @Valid @RequestBody RateRequest request
    ) {
        ServiceRate rate = useCase.createRate(new UtilityUseCase.RateCommand(propertyId, request.serviceName(),
            request.chargeType(), request.pricePerUnit()));
        return ResponseEntity.created(URI.create("/api/service-rates/" + rate.id())).body(RateResponse.from(rate));
    }

    @PatchMapping("/service-rates/{rateId}")
    public RateResponse updateRate(@PathVariable UUID rateId, @Valid @RequestBody RateRequest request) {
        return RateResponse.from(useCase.updateRate(rateId, new UtilityUseCase.RateCommand(null,
            request.serviceName(), request.chargeType(), request.pricePerUnit())));
    }

    public record ReplacementRequest(
        @NotNull @DecimalMin("0") BigDecimal oldFinalValue,
        @NotNull @DecimalMin("0") BigDecimal newStartValue,
        @NotBlank String note
    ) {}

    public record ReadingRequest(
        @NotNull MeterType meterType,
        @NotNull @DecimalMin("0") BigDecimal currentValue,
        LocalDate readingDate,
        @DecimalMin("0") BigDecimal baselineValue,
        @Valid ReplacementRequest replacement,
        @DecimalMin("0") BigDecimal ocrValue,
        @DecimalMin("0") @DecimalMax("1") BigDecimal ocrConfidence
    ) {}

    public record OcrResponse(BigDecimal value, BigDecimal confidence, boolean requiresManualEntry, String engine) {}

    public record RateRequest(
        @NotBlank String serviceName,
        @NotNull ChargeType chargeType,
        @NotNull @DecimalMin("0") BigDecimal pricePerUnit
    ) {}

    public record ReadingResponse(
        UUID id,
        UUID roomId,
        String meterType,
        int periodYear,
        int periodMonth,
        BigDecimal previousValue,
        BigDecimal currentValue,
        BigDecimal consumption,
        boolean replaced,
        BigDecimal oldMeterFinalValue,
        BigDecimal newMeterStartValue,
        String replacementNote,
        LocalDate readingDate,
        boolean hasPhoto,
        BigDecimal ocrValue,
        BigDecimal ocrConfidence,
        boolean ocrCorrected,
        Instant createdAt
    ) {
        static ReadingResponse from(MeterReading reading) {
            return new ReadingResponse(reading.id(), reading.roomId(), reading.meterType().name(),
                reading.periodYear(), reading.periodMonth(), reading.previousValue(), reading.currentValue(),
                reading.consumption(), reading.replaced(), reading.oldMeterFinalValue(),
                reading.newMeterStartValue(), reading.replacementNote(), reading.readingDate(),
                reading.photoKey() != null, reading.ocrValue(), reading.ocrConfidence(), reading.ocrCorrected(),
                reading.createdAt());
        }
    }

    public record RateResponse(UUID id, UUID propertyId, String serviceName, String chargeType,
                               BigDecimal pricePerUnit, Instant updatedAt) {
        static RateResponse from(ServiceRate rate) {
            return new RateResponse(rate.id(), rate.propertyId(), rate.serviceName(), rate.chargeType().name(),
                rate.pricePerUnit(), rate.updatedAt());
        }
    }
}
