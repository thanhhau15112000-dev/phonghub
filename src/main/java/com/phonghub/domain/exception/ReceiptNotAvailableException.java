package com.phonghub.domain.exception;

/** Hóa đơn chưa có khoản thanh toán nào nên chưa có biên lai — trả 409. */
public class ReceiptNotAvailableException extends DomainException {
    public ReceiptNotAvailableException(String message) {
        super(message);
    }
}
