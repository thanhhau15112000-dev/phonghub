package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.HandoverUseCase;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverItem;
import com.phonghub.domain.model.HandoverPhoto;
import com.phonghub.domain.model.HandoverPhotoKind;
import com.phonghub.domain.model.HandoverType;
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

/**
 * Biên bản bàn giao. {@code kind} là "handover" (check-in, đường dẫn đặc tả có sẵn) hoặc "check-out".
 */
@RestController
@RequestMapping("/api")
public class HandoverApiController {

    private static final String KIND = "/contracts/{contractId}/{kind:handover|check-out}";

    private final HandoverUseCase useCase;

    public HandoverApiController(HandoverUseCase useCase) {
        this.useCase = useCase;
    }

    private static HandoverType typeOf(String kind) {
        return "check-out".equals(kind) ? HandoverType.CHECK_OUT : HandoverType.CHECK_IN;
    }

    @PostMapping("/contracts/{contractId}/check-in")
    public ResponseEntity<HandoverResponse> createCheckIn(
        @PathVariable UUID contractId,
        @Valid @RequestBody HandoverRequest request
    ) {
        Handover handover = useCase.create(HandoverType.CHECK_IN, request.toCommand(contractId));
        return ResponseEntity.created(URI.create("/api/contracts/" + contractId + "/handover"))
            .body(response(HandoverType.CHECK_IN, contractId, handover));
    }

    @PostMapping("/contracts/{contractId}/check-out")
    public ResponseEntity<HandoverResponse> createCheckOut(
        @PathVariable UUID contractId,
        @Valid @RequestBody HandoverRequest request
    ) {
        Handover handover = useCase.create(HandoverType.CHECK_OUT, request.toCommand(contractId));
        return ResponseEntity.created(URI.create("/api/contracts/" + contractId + "/check-out"))
            .body(response(HandoverType.CHECK_OUT, contractId, handover));
    }

    @GetMapping(KIND)
    public HandoverResponse get(@PathVariable UUID contractId, @PathVariable String kind) {
        HandoverUseCase.HandoverDetails details = useCase.get(typeOf(kind), contractId);
        return HandoverResponse.from(details);
    }

    @PatchMapping(KIND)
    public HandoverResponse update(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        @Valid @RequestBody HandoverRequest request
    ) {
        return response(typeOf(kind), contractId, useCase.update(typeOf(kind), request.toCommand(contractId)));
    }

    @PostMapping(KIND + "/photos")
    public ResponseEntity<PhotoResponse> addPhoto(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        @RequestParam("kind") HandoverPhotoKind photoKind,
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        HandoverPhoto photo = useCase.addPhoto(typeOf(kind), contractId, photoKind, file.getContentType(),
            file.getBytes());
        return ResponseEntity.status(201).body(PhotoResponse.from(photo));
    }

    @DeleteMapping(KIND + "/photos/{photoId}")
    public ResponseEntity<Void> removePhoto(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        @PathVariable UUID photoId
    ) {
        useCase.removePhoto(typeOf(kind), contractId, photoId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(KIND + "/items/{itemId}")
    public ItemResponse updateItem(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        @PathVariable UUID itemId,
        @Valid @RequestBody ItemRequest request
    ) {
        return ItemResponse.from(useCase.updateItem(typeOf(kind), contractId, itemId,
            new HandoverUseCase.ItemCommand(request.status(), request.conditionNote(), request.deductionAmount())));
    }

    @PostMapping(KIND + "/send")
    public HandoverResponse send(@PathVariable UUID contractId, @PathVariable String kind) {
        return response(typeOf(kind), contractId, useCase.send(typeOf(kind), contractId));
    }

    @PostMapping(KIND + "/confirm")
    public HandoverResponse confirm(@PathVariable UUID contractId, @PathVariable String kind) {
        return response(typeOf(kind), contractId, useCase.confirm(typeOf(kind), contractId));
    }

    @PostMapping(KIND + "/approve")
    public HandoverResponse approve(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        @Valid @RequestBody(required = false) ApproveRequest request
    ) {
        BigDecimal deduction = request == null ? null : request.finalDeduction();
        List<UUID> offsetIds = request == null || request.depositOffsetInvoiceIds() == null
            ? List.of() : request.depositOffsetInvoiceIds();
        return response(typeOf(kind), contractId, useCase.approve(typeOf(kind), contractId, deduction, offsetIds));
    }

    @PostMapping(KIND + "/reject")
    public HandoverResponse reject(
        @PathVariable UUID contractId,
        @PathVariable String kind,
        @Valid @RequestBody RejectRequest request
    ) {
        return response(typeOf(kind), contractId, useCase.reject(typeOf(kind), contractId, request.reason()));
    }

    @PostMapping(KIND + "/reopen")
    public HandoverResponse reopen(@PathVariable UUID contractId, @PathVariable String kind) {
        return response(typeOf(kind), contractId, useCase.reopen(typeOf(kind), contractId));
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

    private HandoverResponse response(HandoverType type, UUID contractId, Handover handover) {
        HandoverUseCase.HandoverDetails details = useCase.get(type, contractId);
        return HandoverResponse.from(new HandoverUseCase.HandoverDetails(handover, details.photos(),
            details.items()));
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

    public record ItemRequest(@NotNull AssetStatus status, String conditionNote, @DecimalMin("0") BigDecimal deductionAmount) {}

    public record ApproveRequest(@DecimalMin("0") BigDecimal finalDeduction, List<UUID> depositOffsetInvoiceIds) {}

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

    public record ItemResponse(
        UUID id,
        UUID assetId,
        String assetName,
        int quantity,
        String status,
        String conditionNote,
        BigDecimal deductionAmount
    ) {
        static ItemResponse from(HandoverItem item) {
            return new ItemResponse(item.id(), item.assetId(), item.assetName(), item.quantity(),
                item.status().name(), item.conditionNote(), item.deductionAmount());
        }
    }

    public record HandoverResponse(
        UUID id,
        UUID contractId,
        String type,
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
        BigDecimal proposedDeduction,
        BigDecimal finalDeduction,
        boolean earlyTermination,
        String terminationReason,
        List<PhotoResponse> photos,
        List<ItemResponse> items
    ) {
        static HandoverResponse from(HandoverUseCase.HandoverDetails details) {
            Handover handover = details.handover();
            return new HandoverResponse(handover.id(), handover.contractId(), handover.type().name(),
                handover.status().name(), handover.initialElectricMeter(), handover.initialWaterMeter(),
                handover.roomCondition(), handover.keysProvided(), handover.contractTermsHash(),
                handover.houseRulesText(), handover.houseRulesHash(), handover.rejectionReason(),
                handover.sentAt(), handover.confirmedByTenant(), handover.confirmedAt(), handover.approvedAt(),
                details.proposedDeduction(), handover.finalDeduction(), handover.earlyTermination(),
                handover.terminationReason(),
                details.photos().stream().map(PhotoResponse::from).toList(),
                details.items().stream().map(ItemResponse::from).toList());
        }
    }
}
