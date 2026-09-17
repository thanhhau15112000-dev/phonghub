package com.phonghub.domain;

import com.phonghub.domain.exception.InvalidRoomStateException;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomTransitionsUnitTest {

    private Room room;

    @BeforeEach
    void setUp() {
        room = Room.create(
            UUID.randomUUID(),
            "P101",
            1,
            new BigDecimal("25.0"),
            new BigDecimal("3500000"),
            2
        );
    }

    @Test
    @DisplayName("New room defaults to AVAILABLE")
    void testInitialState() {
        assertEquals(RoomStatus.AVAILABLE, room.getStatus());
    }

    @Test
    @DisplayName("AVAILABLE can transition to RESERVED, OCCUPIED, and MAINTENANCE")
    void testValidTransitionsFromAvailable() {
        assertTrue(room.canTransitionTo(RoomStatus.RESERVED));
        assertTrue(room.canTransitionTo(RoomStatus.OCCUPIED));
        assertTrue(room.canTransitionTo(RoomStatus.MAINTENANCE));

        room.reserve();
        assertEquals(RoomStatus.RESERVED, room.getStatus());
    }

    @Test
    @DisplayName("RESERVED can cancel back to AVAILABLE or occupy")
    void testValidTransitionsFromReserved() {
        room.reserve();
        assertTrue(room.canTransitionTo(RoomStatus.AVAILABLE));
        assertTrue(room.canTransitionTo(RoomStatus.OCCUPIED));
        assertFalse(room.canTransitionTo(RoomStatus.MAINTENANCE));

        room.cancelReservation();
        assertEquals(RoomStatus.AVAILABLE, room.getStatus());

        room.reserve();
        room.occupy();
        assertEquals(RoomStatus.OCCUPIED, room.getStatus());
    }

    @Test
    @DisplayName("OCCUPIED can vacate to AVAILABLE or MAINTENANCE")
    void testValidTransitionsFromOccupied() {
        room.occupy();
        assertTrue(room.canTransitionTo(RoomStatus.AVAILABLE));
        assertTrue(room.canTransitionTo(RoomStatus.MAINTENANCE));
        assertFalse(room.canTransitionTo(RoomStatus.RESERVED));

        room.vacate(false);
        assertEquals(RoomStatus.AVAILABLE, room.getStatus());

        room.occupy();
        room.vacate(true);
        assertEquals(RoomStatus.MAINTENANCE, room.getStatus());
    }

    @Test
    @DisplayName("MAINTENANCE can only transition to AVAILABLE")
    void testValidTransitionsFromMaintenance() {
        room.putUnderMaintenance();
        assertEquals(RoomStatus.MAINTENANCE, room.getStatus());

        assertTrue(room.canTransitionTo(RoomStatus.AVAILABLE));
        assertFalse(room.canTransitionTo(RoomStatus.OCCUPIED));
        assertFalse(room.canTransitionTo(RoomStatus.RESERVED));

        room.releaseFromMaintenance();
        assertEquals(RoomStatus.AVAILABLE, room.getStatus());
    }

    @Test
    @DisplayName("Invalid transition from MAINTENANCE to OCCUPIED throws InvalidRoomStateException")
    void testInvalidTransitionMaintenanceToOccupied() {
        room.putUnderMaintenance();
        InvalidRoomStateException ex = assertThrows(
            InvalidRoomStateException.class,
            () -> room.transitionTo(RoomStatus.OCCUPIED)
        );
        assertTrue(ex.getMessage().contains("cannot move from MAINTENANCE to OCCUPIED"));
    }

    @Test
    @DisplayName("Invalid transition from RESERVED to MAINTENANCE throws InvalidRoomStateException")
    void testInvalidTransitionReservedToMaintenance() {
        room.reserve();
        InvalidRoomStateException ex = assertThrows(
            InvalidRoomStateException.class,
            room::putUnderMaintenance
        );
        assertTrue(ex.getMessage().contains("must be AVAILABLE or OCCUPIED"));
    }

    @Test
    @DisplayName("Cannot occupy a room already in MAINTENANCE")
    void testCannotOccupyMaintenanceRoom() {
        room.putUnderMaintenance();
        assertThrows(InvalidRoomStateException.class, room::occupy);
    }
}
