package com.phonghub.domain.model;

public enum MaintenanceStatus {
    REPORTED,
    ASSIGNED,
    IN_PROGRESS,
    /** Đã sửa xong, có khoản phí người thuê phải trả; chuyển RESOLVED khi hóa đơn được thanh toán đủ. */
    AWAITING_PAYMENT,
    RESOLVED,
    VERIFIED,
    REJECTED,
    /** Hao mòn tự nhiên (nhánh A): chờ chủ trọ duyệt trước khi giao việc, có hạn duyệt (#202). */
    PENDING_OWNER_APPROVAL,
    /** Lỗi do người thuê (nhánh B): đã gửi báo giá, chờ người thuê phê duyệt hoặc thương lượng (#203). */
    PENDING_TENANT_APPROVAL,
    /** Người báo hoặc người quản lý hủy phiếu trước khi bắt đầu xử lý (#89); giữ lại bản ghi và mốc thời gian. */
    CANCELLED
}
