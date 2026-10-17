package com.phonghub.config;

import com.phonghub.application.port.in.RoomUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomPage;
import com.phonghub.domain.model.RoomSearchCriteria;
import com.phonghub.domain.model.RoomStatus;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cache danh sách phòng theo (người dùng, nhà trọ) và kết quả lọc/phân trang phòng (#84) theo (người dùng, điều kiện
 * lọc) (#88); thao tác ghi phòng xóa cache. Trạng thái phòng còn đổi theo hợp đồng/bảo trì/bàn giao — các luồng đó
 * chạy trong transaction nên được xóa cache khi commit.
 */
public class CachingRoomUseCase implements RoomUseCase {

    private final RoomUseCase delegate;
    private final PhongHubCaches caches;
    private final CurrentUserPort currentUserPort;

    public CachingRoomUseCase(RoomUseCase delegate, PhongHubCaches caches, CurrentUserPort currentUserPort) {
        this.delegate = delegate;
        this.caches = caches;
        this.currentUserPort = currentUserPort;
    }

    @Override
    public List<Room> listRoomsForProperty(UUID propertyId) {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null) {
            return delegate.listRoomsForProperty(propertyId);
        }
        return caches.get(new PhongHubCaches.Key("rooms", user.id(), propertyId),
            () -> delegate.listRoomsForProperty(propertyId));
    }

    /** Trang nhà trọ và API danh sách phòng dùng hàm này (#84). */
    @Override
    public RoomPage searchRooms(RoomSearchCriteria criteria) {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null) {
            return delegate.searchRooms(criteria);
        }
        return caches.get(new PhongHubCaches.Key("rooms.search", user.id(), criteria),
            () -> delegate.searchRooms(criteria));
    }

    @Override
    public Room getRoom(UUID roomId) {
        return delegate.getRoom(roomId);
    }

    @Override
    public Room createRoom(CreateRoomCommand command) {
        return write(() -> delegate.createRoom(command));
    }

    @Override
    public Room updateRoom(UpdateRoomCommand command) {
        return write(() -> delegate.updateRoom(command));
    }

    @Override
    public Room changeRoomStatus(UUID roomId, RoomStatus targetStatus) {
        return write(() -> delegate.changeRoomStatus(roomId, targetStatus));
    }

    @Override
    public Room archiveRoom(UUID roomId) {
        return write(() -> delegate.archiveRoom(roomId));
    }

    @Override
    public Room restoreRoom(UUID roomId) {
        return write(() -> delegate.restoreRoom(roomId));
    }

    /** RoomService không chạy trong transaction nên xóa cache cả khi lỗi (có thể đã ghi một phần). */
    private <T> T write(Supplier<T> action) {
        try {
            return action.get();
        } finally {
            caches.evictAll();
        }
    }
}
