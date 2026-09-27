package com.phonghub.domain;

import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomUpdateUnitTest {

    private Room room;
    private final UUID propertyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        room = Room.create(
            propertyId,
            "101",
            1,
            new BigDecimal("25.0"),
            new BigDecimal("3500000"),
            2
        );
    }

    @Test
    @DisplayName("updateInfo updates editable fields and preserves id, propertyId, status, and createdAt")
    void testUpdateInfoSuccess() {
        var originalId = room.getId();
        var originalPropertyId = room.getPropertyId();
        var originalCreatedAt = room.getCreatedAt();
        var originalStatus = room.getStatus();

        room.updateInfo(
            " 101-VIP ",
            2,
            new BigDecimal("35.5"),
            new BigDecimal("4500000"),
            4
        );

        assertEquals("101-VIP", room.getRoomNumber());
        assertEquals(2, room.getFloor());
        assertEquals(new BigDecimal("35.5"), room.getAreaSqm());
        assertEquals(new BigDecimal("4500000"), room.getBasePrice());
        assertEquals(4, room.getMaxOccupants());

        // Invariants preserved
        assertEquals(originalId, room.getId());
        assertEquals(originalPropertyId, room.getPropertyId());
        assertEquals(originalCreatedAt, room.getCreatedAt());
        assertEquals(originalStatus, room.getStatus());
        assertNotNull(room.getUpdatedAt());
    }

    @Test
    @DisplayName("updateInfo with blank roomNumber throws IllegalArgumentException")
    void testBlankRoomNumberThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            room.updateInfo("", 1, new BigDecimal("25.0"), new BigDecimal("3500000"), 2)
        );
        assertThrows(IllegalArgumentException.class, () ->
            room.updateInfo("   ", 1, new BigDecimal("25.0"), new BigDecimal("3500000"), 2)
        );
        assertThrows(IllegalArgumentException.class, () ->
            room.updateInfo(null, 1, new BigDecimal("25.0"), new BigDecimal("3500000"), 2)
        );
    }

    @Test
    @DisplayName("updateInfo with negative basePrice throws IllegalArgumentException")
    void testNegativeBasePriceThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            room.updateInfo("101", 1, new BigDecimal("25.0"), new BigDecimal("-1"), 2)
        );
        assertThrows(IllegalArgumentException.class, () ->
            room.updateInfo("101", 1, new BigDecimal("25.0"), null, 2)
        );
    }

    @Test
    @DisplayName("updateInfo with zero or negative maxOccupants throws IllegalArgumentException")
    void testInvalidMaxOccupantsThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            room.updateInfo("101", 1, new BigDecimal("25.0"), new BigDecimal("3500000"), 0)
        );
        assertThrows(IllegalArgumentException.class, () ->
            room.updateInfo("101", 1, new BigDecimal("25.0"), new BigDecimal("3500000"), -2)
        );
    }

    @Test
    @DisplayName("updateInfo with negative area throws IllegalArgumentException")
    void testNegativeAreaThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            room.updateInfo("101", 1, new BigDecimal("-5.0"), new BigDecimal("3500000"), 2)
        );
    }
}
