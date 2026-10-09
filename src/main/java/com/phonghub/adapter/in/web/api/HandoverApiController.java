package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.HandoverUseCase;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverItem;
import com.phonghub.domain.model.HandoverPhoto;
import com.phonghub.domain.model.HandoverPhotoKind;
import com.phonghub.domain.model.PropertyHouseRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class HandoverApiController {

    private final HandoverUseCase useCase;

    public HandoverApiController(HandoverUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/contracts/{contractId}/check-in")
    public ResponseEntity<HandoverResponse> create(
        @PathVariable UUID contractId,
        @Valid @RequestBody HandoverRequest request
    ) {
        Handover handover = useCase.create(request.toCommand(contractId));
        return ResponseEntity.created(URI.create("/api/contracts/" + contractId + "/handover"))
            .body(response(contractId, handover));
    }

    @GetMapping("/contracts/{contractId}/handover")
    public HandoverResponse get(@PathVariable UUID contractId) {
        HandoverUseCase.HandoverDetails details = useCase.get(contractId);
        return HandoverResponse.from(details.handover(), details.photos(), details.items());
    }

    @PatchMapping("/contracts/{contractId}/handover")
    public HandoverResponse update(@PathVariable UUID contractId, @Valid @RequestBody HandoverRequest request) {
        return response(contractId, useCase.update(request.toCommand(contractId)));
    }

    @PostMapping("/contracts/{contractId}/handover/photos")
    public ResponseEntity<PhotoResponse> addPhoto(
        @PathVariable UUID contractId,
        @RequestParam HandoverPhotoKind kind,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        HandoverPhoto photo = useCase.addPhoto(contractId, kind, file.getContentType(), file.getBytes());
        return ResponseEntity.status(201).body(PhotoResponse.from(photo));
    }

    @DeleteMapping("/contracts/{contractId}/handover/photos/{photoId}")
    public ResponseEntity<Void> removePhoto(@PathVariable UUID contractId, @PathVariable UUID photoId) {
        useCase.removePhoto(contractId, photoId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/contracts/{contractId}/handover/send")
    public HandoverResponse send(@PathVariable UUID contractId) {
        return response(contractId, useCase.send(contractId));
    }

    @PostMapping("/contracts/{contractId}/handover/confirm")
    public HandoverResponse confirm(@PathVariable UUID contractId) {
        return response(contractId, useCase.confirm(contractId));
    }

    @PostMapping("/contracts/{contractId}/handover/approve")
    public HandoverResponse approve(@PathVariable UUID contractId) {
        return response(contractId, useCase.approve(contractId));
    }

    @PostMapping("/contracts/{contractId}/handover/reject")
    public HandoverResponse reject(@PathVariable UUID contractId, @Valid @RequestBody RejectRequest request) {
        return response(contractId, useCase.reject(contractId, request.reason()));
    }

    @PostMapping("/contracts/{contractId}/handover/reopen")
    public HandoverResponse reopen(@PathVariable UUID contractId) {
        return response(contractId, useCase.reopen(contractId));
    }

    @GetMapping("/properties/{propertyId}/house-rules")
    public ResponseEntity<HouseRulesResponse> getHouseRules(@PathVariable UUID propertyId) {
        PropertyHouseRules rules = useCase.getHouseRules(propertyId);
        return rules == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(HouseRulesResponse.from(rules));
    }

    @PutMapping("/properties/{propertyId}/house-rules")
    public HouseRulesResponse saveHouseRules(
        @PathVariable UUID propertyId,
        @Valid @RequestBody HouseRulesRequest request
    ) {
        return HouseRulesResponse.from(useCase.saveHouseRules(propertyId, request.content()));
    }

    private HandoverResponse response(UUID contractId, Handover handover) {
        HandoverUseCase.HandoverDetails details = useCase.get(contractId);
        return HandoverResponse.from(handover, details.photos(), details.items());
    }

    public record HandoverRequest(
        @NotNull @DecimalMin("0") BigDecimal initialElectricMeter,
        @NotNull @DecimalMin("0") BigDecimal initialWaterMeter,
        @NotBlank String roomCondition,
        String keysProvided
    ) {
        HandoverUseCase.HandoverCommand toCommand(UUID contractId) {
            return new HandoverUseCase.HandoverCommand(contractId, initialElectricMeter, initialWaterMeter,
                roomCondition, keysProvided);
        }
    }

    public record RejectRequest(@NotBlank String reason) {}

    public record HouseRulesRequest(@NotBlank String content) {}

    public record HouseRulesResponse(UUID propertyId, String content, int version, Instant updatedAt) {
        static HouseRulesResponse from(PropertyHouseRules rules) {
            return new HouseRulesResponse(rules.propertyId(), rules.content(), rules.version(), rules.updatedAt());
        }
    }

    public record PhotoResponse(UUID id, String kind, String contentType, Instant createdAt) {
        static PhotoResponse from(HandoverPhoto photo) {
            return new PhotoResponse(photo.id(), photo.kind().name(), photo.contentType(), photo.createdAt());
        }
    }

    public record ItemResponse(UUID assetId, String assetName, int quantity, String status, String conditionNote) {
        static ItemResponse from(HandoverItem item) {
            return new ItemResponse(item.assetId(), item.assetName(), item.quantity(), item.status().name(),
                item.conditionNote());
        }
    }

    public record HandoverResponse(
        UUID id,
        UUID contractId,
        String status,
        BigDecimal initialElectricMeter,
        BigDecimal initialWaterMeter,
        String roomCondition,
        String keysProvided,
        String contractTermsHash,
        String houseRulesText,
        String houseRulesHash,
        String rejectionReason,
        Instant sentAt,
        boolean isConfirmedByTenant,
        Instant confirmedAt,
        Instant approvedAt,
        List<PhotoResponse> photos,
        List<ItemResponse> items
    ) {
        static HandoverResponse from(Handover handover, List<HandoverPhoto> photos, List<HandoverItem> items) {
            return new HandoverResponse(handover.id(), handover.contractId(), handover.status().name(),
                handover.initialElectricMeter(), handover.initialWaterMeter(), handover.roomCondition(),
                handover.keysProvided(), handover.contractTermsHash(), handover.houseRulesText(),
                handover.houseRulesHash(), handover.rejectionReason(), handover.sentAt(),
                handover.confirmedByTenant(), handover.confirmedAt(), handover.approvedAt(),
                photos.stream().map(PhotoResponse::from).toList(), items.stream().map(ItemResponse::from).toList());
        }
    }
}
