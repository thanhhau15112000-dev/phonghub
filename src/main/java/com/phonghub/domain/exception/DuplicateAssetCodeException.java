package com.phonghub.domain.exception;

public class DuplicateAssetCodeException extends DomainException {
    public DuplicateAssetCodeException(String assetCode) {
        super("Mã tài sản đã tồn tại: " + assetCode);
    }
}
