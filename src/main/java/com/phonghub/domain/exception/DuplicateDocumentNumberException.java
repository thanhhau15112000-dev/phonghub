package com.phonghub.domain.exception;

public class DuplicateDocumentNumberException extends DomainException {
    public DuplicateDocumentNumberException() {
        super("Số giấy tờ đã được đăng ký cho một người thuê khác");
    }
}
