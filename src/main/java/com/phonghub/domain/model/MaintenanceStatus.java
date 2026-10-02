package com.phonghub.domain.model;

public enum MaintenanceStatus {
    REPORTED,
    ASSIGNED,
    IN_PROGRESS,
    /** Đã sửa xong, có khoản phí người thuê phải trả; chuyển RESOLVED khi hóa đơn được thanh toán đủ. */
    AWAITING_PAYMENT,
    RESOLVED,
    VERIFIED,
    REJECTED
}
