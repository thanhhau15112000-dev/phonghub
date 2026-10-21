package com.phonghub.adapter.in.web.api;

import com.phonghub.adapter.in.web.api.dto.RoomResponse;
import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomPage;
import com.phonghub.domain.model.RoomSearchCriteria;
import com.phonghub.domain.model.RoomStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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

    public RoomApiController(RoomUseCase roomUseCase) {
        this.roomUseCase = roomUseCase;
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
        RoomSearchCriteria criteria = new RoomSearchCriteria(
            propertyId, status, minPrice, maxPrice, floor, null, page, size, archived);
        if (criteria.isUnfiltered()) {
            return ResponseEntity.ok(roomUseCase.listRoomsForProperty(propertyId).stream()
                .map(RoomResponse::from)
                .toList());
        }
        RoomPage result = roomUseCase.searchRooms(criteria);
        return ResponseEntity.ok()
            .header("X-Total-Count", Long.toString(result.totalElements()))
            .header("X-Page", Integer.toString(result.page()))
            .body(result.items().stream().map(RoomResponse::from).toList());
    }

    @PostMapping("/properties/{propertyId}/rooms")
    public ResponseEntity<RoomResponse> createRoom(
        @PathVariable UUID propertyId,
        @Valid @RequestBody CreateRoomRequest request
    ) {
        Room room = roomUseCase.createRoom(new RoomUseCase.CreateRoomCommand(
            propertyId,
            request.roomNumber(),
            request.floor(),
            request.areaSqm(),
            request.basePrice(),
            request.maxOccupants()
        ));
        RoomResponse response = RoomResponse.from(room);
        return ResponseEntity.created(URI.create("/api/rooms/" + response.id())).body(response);
    }

    /** Ngừng sử dụng phòng (#89): chủ nhà trọ hoặc admin; còn hợp đồng/hóa đơn/phiếu bảo trì dở dang thì 409. */
    @PostMapping("/rooms/{id}/archive")
    public ResponseEntity<RoomResponse> archiveRoom(@PathVariable UUID id) {
        return ResponseEntity.ok(RoomResponse.from(roomUseCase.archiveRoom(id)));
    }

    @PostMapping("/rooms/{id}/restore")
    public ResponseEntity<RoomResponse> restoreRoom(@PathVariable UUID id) {
        return ResponseEntity.ok(RoomResponse.from(roomUseCase.restoreRoom(id)));
    }

    @GetMapping("/rooms/{id}")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable UUID id) {
        Room room = roomUseCase.getRoom(id);
        return ResponseEntity.ok(RoomResponse.from(room));
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
        return ResponseEntity.ok(RoomResponse.from(room));
    }

    @PutMapping("/rooms/{id}/status")
    public ResponseEntity<RoomResponse> updateRoomStatus(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateRoomStatusRequest request
    ) {
        Room room = roomUseCase.changeRoomStatus(id, request.status());
        return ResponseEntity.ok(RoomResponse.from(room));
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
        int maxOccupants
    ) {}

    public record UpdateRoomRequest(
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

        /** Phiên bản phòng khi client đọc; bỏ trống thì không kiểm tra xung đột ở tầng ứng dụng. */
        Long version
    ) {
        public UpdateRoomRequest(String roomNumber, int floor, BigDecimal areaSqm, BigDecimal basePrice, int maxOccupants) {
            this(roomNumber, floor, areaSqm, basePrice, maxOccupants, null);
        }
    }

    public record UpdateRoomStatusRequest(
        @NotNull(message = "Status is required")
        RoomStatus status
    ) {}
}
