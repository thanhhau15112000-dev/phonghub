package com.phonghub.domain.model;

import com.phonghub.domain.exception.InvalidRoomStateException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Room {
    private final UUID id;
    private final UUID propertyId;
    private final String roomNumber;
    private final int floor;
    private final BigDecimal areaSqm;
    private final BigDecimal basePrice;
    private final int maxOccupants;
    private RoomStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public Room(
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
        if (id == null) {
            throw new IllegalArgumentException("Room id cannot be null");
        }
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

        this.id = id;
        this.propertyId = propertyId;
        this.roomNumber = roomNumber.trim();
        this.floor = floor;
        this.areaSqm = areaSqm;
        this.basePrice = basePrice;
        this.maxOccupants = maxOccupants;
        this.status = status != null ? status : RoomStatus.AVAILABLE;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public static Room create(
        UUID propertyId,
        String roomNumber,
        int floor,
        BigDecimal areaSqm,
        BigDecimal basePrice,
        int maxOccupants
    ) {
        Instant now = Instant.now();
        return new Room(
            UUID.randomUUID(),
            propertyId,
            roomNumber,
            floor,
            areaSqm,
            basePrice,
            maxOccupants,
            RoomStatus.AVAILABLE,
            now,
            now
        );
    }

    public boolean canTransitionTo(RoomStatus target) {
        if (target == null) {
            return false;
        }
        if (this.status == target) {
            return true;
        }
        return switch (this.status) {
            case AVAILABLE -> target == RoomStatus.RESERVED || target == RoomStatus.OCCUPIED || target == RoomStatus.MAINTENANCE;
            case RESERVED -> target == RoomStatus.AVAILABLE || target == RoomStatus.OCCUPIED;
            case OCCUPIED -> target == RoomStatus.AVAILABLE || target == RoomStatus.MAINTENANCE;
            case MAINTENANCE -> target == RoomStatus.AVAILABLE;
        };
    }

    public void transitionTo(RoomStatus target) {
        if (target == null) {
            throw new IllegalArgumentException("Target status cannot be null");
        }
        if (this.status == target) {
            return;
        }
        if (!canTransitionTo(target)) {
            throw new InvalidRoomStateException(String.format(
                "Invalid room status transition for room '%s' (ID: %s): cannot move from %s to %s",
                roomNumber, id, status, target
            ));
        }
        this.status = target;
        this.updatedAt = Instant.now();
    }

    public void reserve() {
        if (this.status != RoomStatus.AVAILABLE) {
            throw new InvalidRoomStateException(String.format(
                "Room '%s' cannot be reserved because it is currently %s (must be AVAILABLE)",
                roomNumber, status
            ));
        }
        transitionTo(RoomStatus.RESERVED);
    }

    public void cancelReservation() {
        if (this.status != RoomStatus.RESERVED) {
            throw new InvalidRoomStateException(String.format(
                "Room '%s' reservation cannot be cancelled because it is currently %s (must be RESERVED)",
                roomNumber, status
            ));
        }
        transitionTo(RoomStatus.AVAILABLE);
    }

    public void occupy() {
        if (this.status != RoomStatus.AVAILABLE && this.status != RoomStatus.RESERVED) {
            throw new InvalidRoomStateException(String.format(
                "Room '%s' cannot be occupied because it is currently %s (must be AVAILABLE or RESERVED)",
                roomNumber, status
            ));
        }
        transitionTo(RoomStatus.OCCUPIED);
    }

    public void vacate(boolean requiresMaintenance) {
        if (this.status != RoomStatus.OCCUPIED) {
            throw new InvalidRoomStateException(String.format(
                "Room '%s' cannot be vacated because it is currently %s (must be OCCUPIED)",
                roomNumber, status
            ));
        }
        if (requiresMaintenance) {
            transitionTo(RoomStatus.MAINTENANCE);
        } else {
            transitionTo(RoomStatus.AVAILABLE);
        }
    }

    public void putUnderMaintenance() {
        if (this.status != RoomStatus.AVAILABLE && this.status != RoomStatus.OCCUPIED) {
            throw new InvalidRoomStateException(String.format(
                "Room '%s' cannot be put under maintenance from %s (must be AVAILABLE or OCCUPIED)",
                roomNumber, status
            ));
        }
        transitionTo(RoomStatus.MAINTENANCE);
    }

    public void releaseFromMaintenance() {
        if (this.status != RoomStatus.MAINTENANCE) {
            throw new InvalidRoomStateException(String.format(
                "Room '%s' cannot be released from maintenance because it is %s (must be MAINTENANCE)",
                roomNumber, status
            ));
        }
        transitionTo(RoomStatus.AVAILABLE);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public int getFloor() {
        return floor;
    }

    public BigDecimal getAreaSqm() {
        return areaSqm;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public int getMaxOccupants() {
        return maxOccupants;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Room room = (Room) o;
        return Objects.equals(id, room.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
