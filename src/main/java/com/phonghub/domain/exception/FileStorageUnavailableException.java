package com.phonghub.domain.exception;

/** Kho lưu trữ tệp (Supabase Storage) không nhận hoặc không trả được tệp; người dùng có thể thử lại sau. */
public class FileStorageUnavailableException extends DomainException {

    public FileStorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
