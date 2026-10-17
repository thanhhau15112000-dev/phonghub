package com.phonghub.domain.exception;

public class DuplicateAssetCodeException extends DomainException {
    public DuplicateAssetCodeException(String assetCode) {
        super("Asset code already exists: " + assetCode);
    }
}
