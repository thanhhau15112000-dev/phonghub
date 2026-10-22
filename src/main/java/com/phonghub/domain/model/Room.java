package com.phonghub.domain.model;

import com.phonghub.domain.exception.InvalidRoomStateException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Room {
    private final UUID id;
    private final UUID propertyId;
    private String roomNumber;
    private int floor;
    private BigDecimal areaSqm;
    private BigDecimal basePrice;
    private int maxOccupants;
    private RoomStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;
    /** Thời điểm phòng ngừng sử dụng (#89); null là đang dùng. */
    private Instant archivedAt;

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

    public void updateInfo(
        String roomNumber,
        int floor,
        BigDecimal areaSqm,
        BigDecimal basePrice,
        int maxOccupants
    ) {
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

        this.roomNumber = roomNumber.trim();
        this.floor = floor;
        this.areaSqm = areaSqm;
        this.basePrice = basePrice;
        this.maxOccupants = maxOccupants;
        this.updatedAt = Instant.now();
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
            case OCCUPIED -> target == RoomStatus.MAINTENANCE;
            case MAINTENANCE -> target == RoomStatus.AVAILABLE;
        };
    }

    /**
     * Trạng thái người quản lý được chọn tay. Phòng còn người ở theo hợp đồng (đang hiệu lực hoặc hết hạn chưa trả
     * phòng) chỉ đổi trạng thái theo vòng đời hợp đồng; "Đang thuê" chỉ có được khi kích hoạt hợp đồng.
     */
    public List<RoomStatus> manualTargets(boolean hasResidingContract) {
        if (hasResidingContract) {
            return List.of();
        }
        return Arrays.stream(RoomStatus.values())
            .filter(target -> target != status && target != RoomStatus.OCCUPIED && canTransitionTo(target))
            .toList();
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
                "Không thể chuyển phòng '%s' từ %s sang %s.",
                roomNumber, status.label(), target.label()
            ));
        }
        this.status = target;
        this.updatedAt = Instant.now();
    }

    public void reserve() {
        if (this.status != RoomStatus.AVAILABLE) {
            throw new InvalidRoomStateException(String.format(
                "Phòng '%s' đang %s nên không đặt giữ chỗ được (cần phòng Trống).",
                roomNumber, status.label()
            ));
        }
        transitionTo(RoomStatus.RESERVED);
    }

    public void cancelReservation() {
        if (this.status != RoomStatus.RESERVED) {
            throw new InvalidRoomStateException(String.format(
                "Phòng '%s' đang %s nên không có giữ chỗ để hủy.",
                roomNumber, status.label()
            ));
        }
        transitionTo(RoomStatus.AVAILABLE);
    }

    public void occupy() {
        if (this.status != RoomStatus.AVAILABLE && this.status != RoomStatus.RESERVED) {
            throw new InvalidRoomStateException(String.format(
                "Phòng '%s' đang %s nên chưa cho thuê được (cần phòng Trống hoặc Đã đặt).",
                roomNumber, status.label()
            ));
        }
        transitionTo(RoomStatus.OCCUPIED);
    }

    public void vacate(boolean requiresMaintenance) {
        if (this.status == RoomStatus.MAINTENANCE) {
            // Dữ liệu cũ: phòng có người ở từng bị chuyển sang bảo trì. Vẫn trả phòng được; phòng giữ trạng thái bảo trì
            // cho tới khi các phiếu sửa xong.
            this.updatedAt = Instant.now();
            return;
        }
        if (this.status != RoomStatus.OCCUPIED) {
            throw new InvalidRoomStateException(String.format(
                "Phòng '%s' đang %s nên không trả phòng được (cần phòng Đang thuê).",
                roomNumber, status.label()
            ));
        }
        if (requiresMaintenance) {
            this.status = RoomStatus.MAINTENANCE;
        } else {
            this.status = RoomStatus.AVAILABLE;
        }
        this.updatedAt = Instant.now();
    }

    public void putUnderMaintenance() {
        if (this.status != RoomStatus.AVAILABLE && this.status != RoomStatus.OCCUPIED) {
            throw new InvalidRoomStateException(String.format(
                "Phòng '%s' đang %s nên không chuyển sang bảo trì được.",
                roomNumber, status.label()
            ));
        }
        transitionTo(RoomStatus.MAINTENANCE);
    }

    public void releaseFromMaintenance() {
        if (this.status != RoomStatus.MAINTENANCE) {
            throw new InvalidRoomStateException(String.format(
                "Phòng '%s' đang %s, không ở trạng thái Bảo trì.",
                roomNumber, status.label()
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

    /** Phiên bản dùng cho khóa lạc quan; do tầng lưu trữ gán sau khi đọc/ghi. */
    public Instant getArchivedAt() {
        return archivedAt;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** Ngừng sử dụng phòng: chỉ khi phòng trống hoặc đang bảo trì (không có người ở, không giữ chỗ). */
    public void archive(Instant at) {
        if (archivedAt != null) {
            throw new InvalidRoomStateException("Phòng '" + roomNumber + "' đã ngừng sử dụng trước đó.");
        }
        if (status != RoomStatus.AVAILABLE && status != RoomStatus.MAINTENANCE) {
            throw new InvalidRoomStateException(String.format(
                "Chỉ ngừng sử dụng được phòng đang trống hoặc bảo trì; phòng '%s' đang ở trạng thái %s.",
                roomNumber, status.label()));
        }
        this.archivedAt = at;
        this.updatedAt = at;
    }

    public void restore() {
        if (archivedAt == null) {
            throw new InvalidRoomStateException("Phòng '" + roomNumber + "' đang được sử dụng, không cần khôi phục.");
        }
        this.archivedAt = null;
        this.updatedAt = Instant.now();
    }

    /** Dùng khi đọc từ kho dữ liệu. */
    public void restoreArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public long getVersion() {
        return version;
    }

    public void restoreVersion(long version) {
        this.version = version;
    }
}
