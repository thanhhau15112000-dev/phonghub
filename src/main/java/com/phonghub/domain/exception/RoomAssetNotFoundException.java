package com.phonghub.domain.exception;

import java.util.UUID;

public class RoomAssetNotFoundException extends DomainException {
    public RoomAssetNotFoundException(UUID assetId) {
        super("Không tìm thấy tài sản.");
    }
}
