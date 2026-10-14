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
    Optional<Room> findById(UUID id);
    List<Room> findByPropertyId(UUID propertyId);
    /** Lọc kết hợp + phân trang trong một nhà trọ, sắp theo tầng rồi số phòng. */
    RoomPage search(RoomSearchCriteria criteria);
    List<Room> findByPropertyIdAndStatus(UUID propertyId, RoomStatus status);
    Optional<Room> findByPropertyIdAndRoomNumber(UUID propertyId, String roomNumber);
    List<Room> findAllById(Collection<UUID> ids);
    boolean existsById(UUID id);
}
