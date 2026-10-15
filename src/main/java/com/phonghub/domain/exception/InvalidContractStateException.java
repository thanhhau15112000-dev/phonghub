package com.phonghub.domain.exception;

/** Thao tác không hợp lệ với trạng thái hiện tại của hợp đồng (ví dụ kích hoạt hợp đồng đã ACTIVE) — trả 409. */
public class InvalidContractStateException extends DomainException {
    public InvalidContractStateException(String message) {
        super(message);
    }
}
