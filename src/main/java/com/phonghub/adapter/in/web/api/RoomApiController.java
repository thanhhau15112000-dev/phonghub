package com.phonghub.adapter.in.web.api;

import com.phonghub.adapter.in.web.api.dto.RoomResponse;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.in.RoomSetupUseCase;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.application.service.AuthorizationService;
import com.phonghub.domain.exception.UnauthorizedPropertyAccessException;
import com.phonghub.domain.model.AssetStatus;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomPage;
import com.phonghub.domain.model.RoomSearchCriteria;
import com.phonghub.domain.model.RoomStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RoomApiController {

    private final RoomUseCase roomUseCase;
    private final RoomSetupUseCase roomSetupUseCase;
    private final CurrentUserPort currentUserPort;
    private final AuthorizationService authorizationService;

    @Autowired
    public RoomApiController(RoomUseCase roomUseCase, RoomSetupUseCase roomSetupUseCase,
                             CurrentUserPort currentUserPort, AuthorizationService authorizationService) {
        this.roomUseCase = roomUseCase;
        this.roomSetupUseCase = roomSetupUseCase;
        this.currentUserPort = currentUserPort;
        this.authorizationService = authorizationService;
    }

    /** Constructor giữ tương thích cho các kiểm thử chỉ đọc danh sách/phòng (không kiểm quyền xem giá). */
    public RoomApiController(RoomUseCase roomUseCase) { this(roomUseCase, null, null, null); }

    /** Có được xem giá thuê cơ bản của nhà trọ này không (#326). */
    private boolean canViewPrice(UUID propertyId) {
        return authorizationService == null
            || authorizationService.canViewPropertyFinance(currentUserPort.getCurrentUser(), propertyId);
    }

    private RoomResponse present(Room room) {
        RoomResponse response = RoomResponse.from(room);
        return canViewPrice(room.getPropertyId()) ? response : response.withoutPrice();
    }

    /**
     * Danh sách phòng của nhà trọ. Không có tham số lọc/phân trang thì trả toàn bộ như trước; có thì lọc kết hợp
     * (AND) theo trạng thái, khoảng giá, tầng và phân trang, tổng số khớp nằm ở header {@code X-Total-Count}.
     * Chỉ phân trang khi có {@code size}; truyền {@code page} mà thiếu {@code size} trả 400.
     */
    @GetMapping("/properties/{propertyId}/rooms")
    public ResponseEntity<List<RoomResponse>> listRoomsForProperty(
        @PathVariable UUID propertyId,
        @RequestParam(required = false) RoomStatus status,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice,
        @RequestParam(required = false) Integer floor,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(required = false) Integer size,
        @RequestParam(defaultValue = "false") boolean archived
    ) {
        boolean canViewPrice = canViewPrice(propertyId);
        if (!canViewPrice && (minPrice != null || maxPrice != null)) {
            // Lọc theo khoảng giá sẽ đoán ra được giá thuê (#326)
            throw new UnauthorizedPropertyAccessException("Bạn không có quyền lọc phòng theo giá thuê tại nhà trọ này.");
        }
        RoomSearchCriteria criteria = new RoomSearchCriteria(
            propertyId, status, minPrice, maxPrice, floor, null, page, size, archived);
        if (criteria.isUnfiltered()) {
            return ResponseEntity.ok(roomUseCase.listRoomsForProperty(propertyId).stream()
                .map(room -> canViewPrice ? RoomResponse.from(room) : RoomResponse.from(room).withoutPrice())
                .toList());
        }
        RoomPage result = roomUseCase.searchRooms(criteria);
        return ResponseEntity.ok()
            .header("X-Total-Count", Long.toString(result.totalElements()))
            .header("X-Page", Integer.toString(result.page()))
            .body(result.items().stream()
                .map(room -> canViewPrice ? RoomResponse.from(room) : RoomResponse.from(room).withoutPrice())
                .toList());
    }

    @PostMapping("/properties/{propertyId}/rooms")
    public ResponseEntity<RoomResponse> createRoom(
        @PathVariable UUID propertyId,
        @Valid @RequestBody CreateRoomRequest request
    ) {
        if (roomSetupUseCase == null) throw new IllegalStateException("Room setup use case is required");
        List<RoomSetupUseCase.AssetInput> assets = request.assets() == null ? List.of() : request.assets().stream()
            .map(item -> new RoomSetupUseCase.AssetInput(item.name(), item.quantity(), item.status(), item.note(), item.estimatedValue()))
            .toList();
        Room room = roomSetupUseCase.createRoom(new RoomSetupUseCase.CreateRoomCommand(propertyId,
            request.roomNumber(), request.floor(), request.areaSqm(), request.basePrice(), request.maxOccupants(),
            request.electricMeter(), request.waterMeter(), request.conditionNote(), Boolean.TRUE.equals(request.noAssets()),
            request.assetTemplate(), assets, request.electricMeterPhotoKey(), request.waterMeterPhotoKey())).room();
        RoomResponse response = present(room);
        return ResponseEntity.created(URI.create("/api/rooms/" + response.id())).body(response);
    }

    @PostMapping("/properties/{propertyId}/rooms/batch")
    public ResponseEntity<List<RoomResponse>> createRoomsBatch(@PathVariable UUID propertyId,
            @Valid @RequestBody CreateRoomsBatchRequest request) {
        List<RoomSetupUseCase.RoomReadingInput> readings = request.rooms().stream()
            .map(room -> new RoomSetupUseCase.RoomReadingInput(room.roomNumber(), room.electricMeter(), room.waterMeter()))
            .toList();
        List<Room> rooms = roomSetupUseCase.createRooms(new RoomSetupUseCase.BulkCreateCommand(propertyId,
            request.floor(), request.areaSqm(), request.basePrice(), request.maxOccupants(), request.conditionNote(),
            request.assetTemplate(), readings)).stream().map(RoomSetupUseCase.RoomSetupResult::room).toList();
        return ResponseEntity.status(201).body(rooms.stream().map(this::present).toList());
    }

    /** Ngừng sử dụng phòng (#89): chủ nhà trọ hoặc admin; còn hợp đồng/hóa đơn/phiếu bảo trì dở dang thì 409. */
    @PostMapping("/rooms/{id}/archive")
    public ResponseEntity<RoomResponse> archiveRoom(@PathVariable UUID id) {
        return ResponseEntity.ok(present(roomUseCase.archiveRoom(id)));
    }

    @PostMapping("/rooms/{id}/restore")
    public ResponseEntity<RoomResponse> restoreRoom(@PathVariable UUID id) {
        return ResponseEntity.ok(present(roomUseCase.restoreRoom(id)));
    }

    @GetMapping("/rooms/{id}")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable UUID id) {
        Room room = roomUseCase.getRoom(id);
        return ResponseEntity.ok(present(room));
    }

    @PutMapping("/rooms/{id}")
    public ResponseEntity<RoomResponse> updateRoom(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateRoomRequest request
    ) {
        Room room = roomUseCase.updateRoom(new RoomUseCase.UpdateRoomCommand(
            id,
            request.roomNumber(),
            request.floor(),
            request.areaSqm(),
            request.basePrice(),
            request.maxOccupants(),
            request.version()
        ));
        return ResponseEntity.ok(present(room));
    }

    @PutMapping("/rooms/{id}/status")
    public ResponseEntity<RoomResponse> updateRoomStatus(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateRoomStatusRequest request
    ) {
        Room room = roomUseCase.changeRoomStatus(id, request.status());
        return ResponseEntity.ok(present(room));
    }

    public record CreateRoomRequest(
        @NotBlank(message = "Room number is required")
        String roomNumber,

        int floor,

        @DecimalMin(value = "0.0", message = "Area cannot be negative")
        BigDecimal areaSqm,

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.0", message = "Base price cannot be negative")
        BigDecimal basePrice,

        @Min(value = 1, message = "Max occupants must be at least 1")
        int maxOccupants,

        @NotNull(message = "Initial electric meter reading is required")
        @DecimalMin(value = "0.0", message = "Electric meter cannot be negative")
        BigDecimal electricMeter,

        @NotNull(message = "Initial water meter reading is required")
        @DecimalMin(value = "0.0", message = "Water meter cannot be negative")
        BigDecimal waterMeter,

        @NotBlank(message = "Initial condition note is required")
        String conditionNote,

        @NotNull(message = "Confirm whether the room has no assets")
        Boolean noAssets,

        String assetTemplate,

        String electricMeterPhotoKey,
        String waterMeterPhotoKey,

        List<@Valid AssetRequest> assets
    ) {
        public CreateRoomRequest(String roomNumber, int floor, BigDecimal areaSqm, BigDecimal basePrice, int maxOccupants) {
            this(roomNumber, floor, areaSqm, basePrice, maxOccupants, BigDecimal.ZERO, BigDecimal.ZERO,
                "Hiện trạng ban đầu", true, "MANUAL", null, null, List.of());
        }
    }

    public record AssetRequest(@NotBlank String name, @Min(1) int quantity, @NotNull AssetStatus status,
                               String note, @DecimalMin("0.0") BigDecimal estimatedValue) {}

    public record CreateRoomsBatchRequest(
        int floor,
        @DecimalMin("0.0") BigDecimal areaSqm,
        @NotNull @DecimalMin("0.0") BigDecimal basePrice,
        @Min(1) int maxOccupants,
        @NotBlank String conditionNote,
        @NotBlank String assetTemplate,
        @NotEmpty @Size(max = 50) List<@Valid BatchRoomRequest> rooms
    ) {}

    public record BatchRoomRequest(@NotBlank String roomNumber, @NotNull @DecimalMin("0.0") BigDecimal electricMeter,
                                   @NotNull @DecimalMin("0.0") BigDecimal waterMeter) {}

    public record UpdateRoomRequest(
        @NotBlank(message = "Room number is required")
        String roomNumber,

        int floor,

        @DecimalMin(value = "0.0", message = "Area cannot be negative")
        BigDecimal areaSqm,

        /** Để trống thì giữ giá hiện tại; người không có quyền tài chính không đọc được giá (#326). */
        @DecimalMin(value = "0.0", message = "Base price cannot be negative")
        BigDecimal basePrice,

        @Min(value = 1, message = "Max occupants must be at least 1")
        int maxOccupants,

        /** Phiên bản phòng client đã đọc (trường version của phòng); bắt buộc để phát hiện sửa đồng thời (#314). */
        @NotNull(message = "Thiếu version của phòng: hãy tải lại thông tin phòng rồi gửi kèm version.")
        Long version
    ) {
    }

    public record UpdateRoomStatusRequest(
        @NotNull(message = "Status is required")
        RoomStatus status
    ) {}
}
