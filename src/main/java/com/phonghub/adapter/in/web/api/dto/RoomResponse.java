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
    Instant updatedAt,
    Instant archivedAt,
    /** Gửi lại khi sửa phòng để phát hiện hai người sửa cùng lúc (#314). */
    long version
) {
    /** Bản trả về cho người không có quyền xem tiền của nhà trọ: bỏ giá thuê cơ bản (#326). */
    public RoomResponse withoutPrice() {
        return new RoomResponse(id, propertyId, roomNumber, floor, areaSqm, null, maxOccupants, status, createdAt,
            updatedAt, archivedAt, version);
    }

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
            room.getUpdatedAt(),
            room.getArchivedAt(),
            room.getVersion()
        );
    }
}
