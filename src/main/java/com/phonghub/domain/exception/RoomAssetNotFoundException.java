package com.phonghub.domain.exception;

import java.util.UUID;

public class RoomAssetNotFoundException extends DomainException {
    public RoomAssetNotFoundException(UUID assetId) {
        super("Room asset not found: " + assetId);
    }
}
