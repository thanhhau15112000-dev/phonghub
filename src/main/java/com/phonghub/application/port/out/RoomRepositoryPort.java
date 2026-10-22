package com.phonghub.application.port.out;

import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomPage;
import com.phonghub.domain.model.RoomSearchCriteria;
import com.phonghub.domain.model.RoomStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepositoryPort {
    Room save(Room room);
    /** Kể cả phòng đã ngừng sử dụng (#89), để tra lại hợp đồng/hóa đơn cũ. */
    Optional<Room> findById(UUID id);
    /** Chỉ phòng đang sử dụng; phòng đã ngừng sử dụng xem qua {@link #search} với {@code archived = true}. */
    List<Room> findByPropertyId(UUID propertyId);
    /** Lọc kết hợp + phân trang trong một nhà trọ, sắp theo tầng rồi số phòng. */
    RoomPage search(RoomSearchCriteria criteria);
    /** Chỉ phòng đang sử dụng. */
    List<Room> findByPropertyIdAndStatus(UUID propertyId, RoomStatus status);
    /** Kể cả phòng đã ngừng sử dụng: số phòng vẫn được giữ, không tạo trùng. */
    Optional<Room> findByPropertyIdAndRoomNumber(UUID propertyId, String roomNumber);
    List<Room> findAllById(Collection<UUID> ids);
    boolean existsById(UUID id);
}
