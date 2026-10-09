package com.phonghub.domain.exception;

public class DuplicateServiceRateException extends DomainException {
    public DuplicateServiceRateException(String serviceName) {
        super("Nhà trọ đã có đơn giá cho dịch vụ '" + serviceName + "'.");
    }
}
