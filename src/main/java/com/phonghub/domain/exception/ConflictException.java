package com.phonghub.domain.exception;

/**
 * Thao tác không thực hiện được vì còn dữ liệu ràng buộc hoặc trạng thái hiện tại không cho phép (#89), ví dụ ẩn nhà
 * trọ còn hợp đồng hiệu lực hay hủy hóa đơn đã có tiền. API trả 409.
 */
public class ConflictException extends DomainException {
    public ConflictException(String message) {
        super(message);
    }
}
