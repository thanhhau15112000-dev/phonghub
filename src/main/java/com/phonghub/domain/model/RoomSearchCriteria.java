package com.phonghub.domain.model;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;

/**
 * Điều kiện lọc phòng trong một nhà trọ (#84). Các điều kiện kết hợp bằng AND; điều kiện null thì bỏ qua.
 *
 * @param restrictToRoomIds chỉ xét các phòng này (người thuê chỉ thấy phòng của mình); null là không giới hạn
 * @param size              số dòng mỗi trang; null là không phân trang (lấy hết)
 */
public record RoomSearchCriteria(
    UUID propertyId,
    RoomStatus status,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    Integer floor,
    Collection<UUID> restrictToRoomIds,
    int page,
    Integer size,
    /** true: chỉ phòng đã ngừng sử dụng (#89); false (mặc định): chỉ phòng đang dùng. */
    boolean archived
) {
    public RoomSearchCriteria(UUID propertyId, RoomStatus status, BigDecimal minPrice, BigDecimal maxPrice,
                              Integer floor, Collection<UUID> restrictToRoomIds, int page, Integer size) {
        this(propertyId, status, minPrice, maxPrice, floor, restrictToRoomIds, page, size, false);
    }

    public static final int MAX_PAGE_SIZE = 100;

    public RoomSearchCriteria {
        if (propertyId == null) {
            throw new IllegalArgumentException("Property id cannot be null");
        }
        if (minPrice != null && minPrice.signum() < 0) {
            throw new IllegalArgumentException("Giá tối thiểu không được âm.");
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new IllegalArgumentException("Giá tối đa không được âm.");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("Giá tối thiểu không được lớn hơn giá tối đa.");
        }
        if (page < 0) {
            throw new IllegalArgumentException("Số trang không được âm.");
        }
        if (size != null && (size < 1 || size > MAX_PAGE_SIZE)) {
            throw new IllegalArgumentException("Kích thước trang phải từ 1 đến " + MAX_PAGE_SIZE + ".");
        }
        if (size == null && page > 0) {
            throw new IllegalArgumentException("Cần truyền kích thước trang (size) khi chọn trang.");
        }
    }

    /** Điều kiện lọc tối thiểu: toàn bộ phòng của nhà trọ, không phân trang. */
    public static RoomSearchCriteria all(UUID propertyId) {
        return new RoomSearchCriteria(propertyId, null, null, null, null, null, 0, null);
    }

    public boolean isUnfiltered() {
        return status == null && minPrice == null && maxPrice == null && floor == null && size == null && !archived;
    }

    public RoomSearchCriteria restrictedTo(Collection<UUID> roomIds) {
        return new RoomSearchCriteria(propertyId, status, minPrice, maxPrice, floor, roomIds, page, size, archived);
    }
}
