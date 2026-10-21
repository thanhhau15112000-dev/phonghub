package com.phonghub.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.phonghub.domain.exception.InvalidRoomStateException;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomSearchCriteria;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #89: quy tắc ngừng sử dụng phòng ở tầng domain. */
class RoomArchiveUnitTest {

    private static Room room(RoomStatus status) {
        return new Room(UUID.randomUUID(), UUID.randomUUID(), "P1", 1, null, new BigDecimal("3000000"), 2, status,
            null, null);
    }

    @Test
    @DisplayName("Phòng trống hoặc bảo trì ngừng sử dụng được, dùng lại được; ngừng hai lần hay dùng lại phòng đang dùng thì lỗi")
    void archiveAndRestore() {
        Instant at = Instant.parse("2026-10-01T00:00:00Z");
        for (RoomStatus status : List.of(RoomStatus.AVAILABLE, RoomStatus.MAINTENANCE)) {
            Room room = room(status);
            room.archive(at);
            assertTrue(room.isArchived());
            assertEquals(at, room.getArchivedAt());
            assertEquals(status, room.getStatus(), "trạng thái phòng giữ nguyên");
            assertThrows(InvalidRoomStateException.class, () -> room.archive(at));
            room.restore();
            assertFalse(room.isArchived());
            assertNull(room.getArchivedAt());
            assertThrows(InvalidRoomStateException.class, room::restore);
        }
    }

    @Test
    @DisplayName("Phòng đang ở hoặc giữ chỗ không ngừng sử dụng được")
    void occupiedOrReservedCannotBeArchived() {
        for (RoomStatus status : List.of(RoomStatus.OCCUPIED, RoomStatus.RESERVED)) {
            Room room = room(status);
            InvalidRoomStateException ex = assertThrows(InvalidRoomStateException.class,
                () -> room.archive(Instant.now()));
            assertTrue(ex.getMessage().contains("đang trống hoặc bảo trì"));
            assertFalse(room.isArchived());
        }
    }

    @Test
    @DisplayName("Tiêu chí tìm kiếm mặc định là phòng đang dùng; archived=true không bị coi là 'không lọc' và giữ khi giới hạn phòng")
    void searchCriteriaArchivedFlag() {
        UUID propertyId = UUID.randomUUID();
        RoomSearchCriteria active = RoomSearchCriteria.all(propertyId);
        assertFalse(active.archived());
        assertTrue(active.isUnfiltered());

        RoomSearchCriteria archived = new RoomSearchCriteria(propertyId, null, null, null, null, null, 0, null, true);
        assertFalse(archived.isUnfiltered(), "không được trả nhầm danh sách phòng đang dùng");
        assertTrue(archived.restrictedTo(List.of(UUID.randomUUID())).archived());
    }
}
