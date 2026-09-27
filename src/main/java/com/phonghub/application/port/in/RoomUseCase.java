package com.phonghub.application.port.in;

import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RoomUseCase {
    Room createRoom(CreateRoomCommand command);
    Room updateRoom(UpdateRoomCommand command);
    List<Room> listRoomsForProperty(UUID propertyId);
    Room getRoom(UUID roomId);
    Room changeRoomStatus(UUID roomId, RoomStatus targetStatus);

    record CreateRoomCommand(
        UUID propertyId,
        String roomNumber,
        int floor,
        BigDecimal areaSqm,
        BigDecimal basePrice,
        int maxOccupants
    ) {
        public CreateRoomCommand {
            if (propertyId == null) {
                throw new IllegalArgumentException("Property id cannot be null");
            }
            if (roomNumber == null || roomNumber.isBlank()) {
                throw new IllegalArgumentException("Room number cannot be blank");
            }
            if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Base price cannot be negative");
            }
            if (maxOccupants <= 0) {
                throw new IllegalArgumentException("Max occupants must be positive");
            }
            if (areaSqm != null && areaSqm.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Area cannot be negative");
            }
        }
    }

    record UpdateRoomCommand(
        UUID roomId,
        String roomNumber,
        int floor,
        BigDecimal areaSqm,
        BigDecimal basePrice,
        int maxOccupants
    ) {
        public UpdateRoomCommand {
            if (roomId == null) {
                throw new IllegalArgumentException("Room id cannot be null");
            }
            if (roomNumber == null || roomNumber.isBlank()) {
                throw new IllegalArgumentException("Room number cannot be blank");
            }
            if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Base price cannot be negative");
            }
            if (maxOccupants <= 0) {
                throw new IllegalArgumentException("Max occupants must be positive");
            }
            if (areaSqm != null && areaSqm.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Area cannot be negative");
            }
        }
    }
}
