package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.RoomAssetUseCase;
import com.phonghub.domain.model.AssetEventType;
import com.phonghub.domain.model.RoomAsset;
import com.phonghub.domain.model.RoomAssetEvent;
import com.phonghub.adapter.in.web.AssetQr;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RoomAssetApiController {

    private final RoomAssetUseCase useCase;

    public RoomAssetApiController(RoomAssetUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/rooms/{roomId}/assets")
    public List<AssetResponse> list(@PathVariable UUID roomId) {
        return useCase.list(roomId).stream().map(AssetResponse::from).toList();
    }

    @PostMapping("/rooms/{roomId}/assets")
    public ResponseEntity<AssetResponse> create(@PathVariable UUID roomId, @Valid @RequestBody AssetRequest request) {
        RoomAsset asset = useCase.create(new RoomAssetUseCase.CreateAssetCommand(
            roomId, request.assetCode(), request.name(), request.serialNumber(), request.quantity(),
            request.condition(), request.imageUrl(), request.estimatedValue()
        ));
        return ResponseEntity.created(URI.create("/api/assets/" + asset.id())).body(AssetResponse.from(asset));
    }

    @PostMapping("/rooms/{roomId}/assets/from-template")
    public ResponseEntity<List<AssetResponse>> createFromTemplate(
        @PathVariable UUID roomId,
        @RequestParam String template
    ) {
        List<AssetResponse> created = useCase.createFromTemplate(roomId, template).stream()
            .map(AssetResponse::from).toList();
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping("/assets/{assetId}")
    public AssetResponse get(@PathVariable UUID assetId) {
        return AssetResponse.from(useCase.get(assetId));
    }

    @PatchMapping("/assets/{assetId}")
    public AssetResponse update(@PathVariable UUID assetId, @Valid @RequestBody AssetRequest request) {
        return AssetResponse.from(useCase.update(new RoomAssetUseCase.UpdateAssetCommand(
            assetId, request.name(), request.serialNumber(), request.quantity(),
            request.condition(), request.imageUrl(), request.estimatedValue()
        )));
    }

    @DeleteMapping("/assets/{assetId}")
    public ResponseEntity<Void> delete(@PathVariable UUID assetId) {
        useCase.delete(assetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/assets/{assetId}/events")
    public List<EventResponse> history(@PathVariable UUID assetId) {
        return useCase.history(assetId).stream().map(EventResponse::from).toList();
    }

    @PostMapping("/assets/{assetId}/events")
    public ResponseEntity<EventResponse> recordEvent(
        @PathVariable UUID assetId,
        @Valid @RequestBody EventRequest request
    ) {
        RoomAssetEvent event = useCase.recordEvent(new RoomAssetUseCase.RecordEventCommand(
            assetId, request.type(), request.description(), request.cost(), request.maintenanceTicketId()
        ));
        return ResponseEntity.status(201).body(EventResponse.from(event));
    }

    @GetMapping(value = "/assets/{assetId}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@PathVariable UUID assetId, HttpServletRequest request) {
        return AssetQr.response(useCase.get(assetId), request);
    }

    public record AssetRequest(
        @Size(max = 40) String assetCode,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String serialNumber,
        @Min(1) int quantity,
        @Size(max = 255) String condition,
        String imageUrl,
        @DecimalMin("0") BigDecimal estimatedValue
    ) {}

    public record EventRequest(
        @NotNull AssetEventType type,
        @NotBlank String description,
        @DecimalMin("0") BigDecimal cost,
        UUID maintenanceTicketId
    ) {}

    public record AssetResponse(
        UUID id,
        UUID roomId,
        String assetCode,
        String name,
        String serialNumber,
        int quantity,
        String status,
        String condition,
        String imageUrl,
        BigDecimal estimatedValue,
        Instant createdAt,
        Instant updatedAt
    ) {
        static AssetResponse from(RoomAsset asset) {
            return new AssetResponse(asset.id(), asset.roomId(), asset.assetCode(), asset.name(),
                asset.serialNumber(), asset.quantity(), asset.status().name(), asset.note(), asset.imageUrl(),
                asset.estimatedValue(), asset.createdAt(), asset.updatedAt());
        }
    }

    public record EventResponse(
        UUID id,
        UUID assetId,
        String type,
        String description,
        BigDecimal cost,
        UUID maintenanceTicketId,
        Instant createdAt
    ) {
        static EventResponse from(RoomAssetEvent event) {
            return new EventResponse(event.id(), event.assetId(), event.type().name(), event.description(),
                event.cost(), event.maintenanceTicketId(), event.createdAt());
        }
    }
}
