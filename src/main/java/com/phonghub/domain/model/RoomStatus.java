package com.phonghub.domain.model;

public enum RoomStatus {
    AVAILABLE("Trống"),
    RESERVED("Đã đặt"),
    OCCUPIED("Đang thuê"),
    MAINTENANCE("Bảo trì");

    private final String label;

    RoomStatus(String label) {
        this.label = label;
    }

    /** Tên trạng thái tiếng Việt dùng trong thông báo cho người dùng. */
    public String label() {
        return label;
    }
}
