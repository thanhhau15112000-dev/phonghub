package com.phonghub.application.port.in;

import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomPage;
import com.phonghub.domain.model.RoomSearchCriteria;
import com.phonghub.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RoomUseCase {
    Room createRoom(CreateRoomCommand command);
    Room updateRoom(UpdateRoomCommand command);

    /**
     * Lọc phòng của một nhà trọ theo trạng thái, khoảng giá, tầng và phân trang. Giữ nguyên phân quyền của
     * {@link #listRoomsForProperty(UUID)}: người thuê chỉ thấy phòng của mình.
     */
    RoomPage searchRooms(RoomSearchCriteria criteria);
    List<Room> listRoomsForProperty(UUID propertyId);
    Room getRoom(UUID roomId);
    Room changeRoomStatus(UUID roomId, RoomStatus targetStatus);

    /**
     * Ngừng sử dụng (xóa mềm) phòng (#89), chỉ chủ nhà trọ hoặc admin. Phòng phải trống hoặc đang bảo trì; còn hợp đồng
     * chưa kết thúc, hóa đơn chưa thu hay phiếu bảo trì chưa xong thì từ chối (409).
     */
    Room archiveRoom(UUID roomId);

    /** Dùng lại phòng đã ngừng sử dụng (chủ nhà trọ hoặc admin). */
    Room restoreRoom(UUID roomId);

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
                throw new IllegalArgumentException("Số phòng không được để trống.");
            }
            if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Giá thuê không được âm.");
            }
            if (maxOccupants <= 0) {
                throw new IllegalArgumentException("Số người tối đa phải lớn hơn 0.");
            }
            if (areaSqm != null && areaSqm.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Diện tích không được âm.");
            }
        }
    }

    record UpdateRoomCommand(
        UUID roomId,
        String roomNumber,
        int floor,
        BigDecimal areaSqm,
        BigDecimal basePrice,
        int maxOccupants,
        Long expectedVersion
    ) {
        public UpdateRoomCommand(
            UUID roomId,
            String roomNumber,
            int floor,
            BigDecimal areaSqm,
            BigDecimal basePrice,
            int maxOccupants
        ) {
            this(roomId, roomNumber, floor, areaSqm, basePrice, maxOccupants, null);
        }

        public UpdateRoomCommand {
            if (roomId == null) {
                throw new IllegalArgumentException("Room id cannot be null");
            }
            if (roomNumber == null || roomNumber.isBlank()) {
                throw new IllegalArgumentException("Số phòng không được để trống.");
            }
            if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Giá thuê không được âm.");
            }
            if (maxOccupants <= 0) {
                throw new IllegalArgumentException("Số người tối đa phải lớn hơn 0.");
            }
            if (areaSqm != null && areaSqm.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Diện tích không được âm.");
            }
        }
    }
}
