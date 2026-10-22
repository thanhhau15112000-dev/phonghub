package com.phonghub.domain;

import com.phonghub.domain.exception.InvalidRoomStateException;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.util.List;
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
    @DisplayName("OCCUPIED can vacate to AVAILABLE or MAINTENANCE, but cannot transition to AVAILABLE directly")
    void testValidTransitionsFromOccupied() {
        room.occupy();
        assertFalse(room.canTransitionTo(RoomStatus.AVAILABLE));
        assertTrue(room.canTransitionTo(RoomStatus.MAINTENANCE));
        assertFalse(room.canTransitionTo(RoomStatus.RESERVED));

        assertThrows(InvalidRoomStateException.class, () -> room.transitionTo(RoomStatus.AVAILABLE));

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
        assertTrue(ex.getMessage().contains("từ Bảo trì sang Đang thuê"));
    }

    @Test
    @DisplayName("Invalid transition from RESERVED to MAINTENANCE throws InvalidRoomStateException")
    void testInvalidTransitionReservedToMaintenance() {
        room.reserve();
        InvalidRoomStateException ex = assertThrows(
            InvalidRoomStateException.class,
            room::putUnderMaintenance
        );
        assertTrue(ex.getMessage().contains("không chuyển sang bảo trì được"));
    }

    @Test
    @DisplayName("Cannot occupy a room already in MAINTENANCE")
    void testCannotOccupyMaintenanceRoom() {
        room.putUnderMaintenance();
        assertThrows(InvalidRoomStateException.class, room::occupy);
    }

    @Test
    @DisplayName("Đổi tay: phòng có người ở theo hợp đồng không có lựa chọn nào; không bao giờ tự đặt Đang thuê")
    void testManualTargets() {
        assertEquals(List.of(RoomStatus.RESERVED, RoomStatus.MAINTENANCE), room.manualTargets(false));
        assertTrue(room.manualTargets(true).isEmpty());

        room.reserve();
        assertEquals(List.of(RoomStatus.AVAILABLE), room.manualTargets(false));

        room.occupy();
        assertTrue(room.manualTargets(true).isEmpty());
        // Phòng Đang thuê mà không còn hợp đồng (dữ liệu cũ) chỉ đi qua Bảo trì rồi mới Trống
        assertEquals(List.of(RoomStatus.MAINTENANCE), room.manualTargets(false));
    }

    @Test
    @DisplayName("Dữ liệu cũ: phòng có người ở đang Bảo trì vẫn trả phòng được và giữ Bảo trì")
    void testVacateFromMaintenanceKeepsMaintenance() {
        room.occupy();
        room.putUnderMaintenance();

        room.vacate(false);

        assertEquals(RoomStatus.MAINTENANCE, room.getStatus());
    }
}
