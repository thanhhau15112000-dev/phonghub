package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.domain.model.Room;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RoomApiController {

    private final RoomUseCase roomUseCase;

    public RoomApiController(RoomUseCase roomUseCase) {
        this.roomUseCase = roomUseCase;
    }

    @GetMapping("/properties/{propertyId}/rooms")
    public ResponseEntity<List<Room>> listRoomsForProperty(@PathVariable UUID propertyId) {
        return ResponseEntity.ok(roomUseCase.listRoomsForProperty(propertyId));
    }

    @PostMapping("/properties/{propertyId}/rooms")
    public ResponseEntity<Room> createRoom(
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
        return ResponseEntity.created(URI.create("/api/rooms/" + room.getId())).body(room);
    }

    @GetMapping("/rooms/{id}")
    public ResponseEntity<Room> getRoom(@PathVariable UUID id) {
        return ResponseEntity.ok(roomUseCase.getRoom(id));
    }

    @PutMapping("/rooms/{id}/status")
    public ResponseEntity<Room> updateRoomStatus(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateRoomStatusRequest request
    ) {
        Room room = roomUseCase.changeRoomStatus(id, request.status());
        return ResponseEntity.ok(room);
    }

    public record CreateRoomRequest(
        @NotBlank(message = "Room number is required")
        String roomNumber,

        int floor,

        BigDecimal areaSqm,

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.0", message = "Base price cannot be negative")
        BigDecimal basePrice,

        @Min(value = 1, message = "Max occupants must be at least 1")
        int maxOccupants
    ) {}

    public record UpdateRoomStatusRequest(
        @NotNull(message = "Status is required")
        RoomStatus status
    ) {}
}
