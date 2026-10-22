package com.phonghub.domain.model;

public enum UserRole {
    ADMIN,
    OWNER,
    STAFF,
    TECHNICIAN,
    TENANT;

    /** Kỹ thuật viên chỉ nhận và xử lý phiếu, không tạo phiếu (MaintenanceService.createTicket). */
    public boolean canReportMaintenance() {
        return this != TECHNICIAN;
    }
}
