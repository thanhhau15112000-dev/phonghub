package com.phonghub.domain.model;

import java.util.List;

/** Một trang kết quả lọc phòng cùng tổng số phòng khớp điều kiện. */
public record RoomPage(List<Room> items, long totalElements, int page, Integer size) {

    public RoomPage {
        items = List.copyOf(items);
    }

    public int totalPages() {
        if (size == null || size <= 0) {
            return totalElements == 0 ? 0 : 1;
        }
        return (int) ((totalElements + size - 1) / size);
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return page + 1 < totalPages();
    }
}
