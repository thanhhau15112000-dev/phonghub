package com.phonghub.domain.exception;

/** Thao tác không hợp lệ với trạng thái hiện tại của phiếu bảo trì (#83) — trả 409. */
public class InvalidMaintenanceStateException extends MaintenanceTicketException {
    public InvalidMaintenanceStateException(String message) {
        super(message);
    }
}
