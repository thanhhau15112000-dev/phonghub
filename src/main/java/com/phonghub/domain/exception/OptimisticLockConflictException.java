package com.phonghub.domain.exception;

/** Bản ghi đã bị người khác cập nhật sau khi bạn đọc; cần tải lại rồi thao tác lại. */
public class OptimisticLockConflictException extends DomainException {
    public OptimisticLockConflictException(String entityName) {
        super(entityName + " vừa được người khác cập nhật. Vui lòng tải lại trang và thực hiện lại.");
    }
}
