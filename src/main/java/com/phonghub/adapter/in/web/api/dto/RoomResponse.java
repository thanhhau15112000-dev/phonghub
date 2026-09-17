package com.phonghub.adapter.in.web.api.dto;

import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RoomResponse(
    UUID id,
    UUID propertyId,
    String roomNumber,
    int floor,
    BigDecimal areaSqm,
    BigDecimal basePrice,
    int maxOccupants,
    RoomStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(
            room.getId(),
            room.getPropertyId(),
            room.getRoomNumber(),
            room.getFloor(),
            room.getAreaSqm(),
            room.getBasePrice(),
            room.getMaxOccupants(),
            room.getStatus(),
            room.getCreatedAt(),
            room.getUpdatedAt()
        );
    }
}
