package com.phonghub.application.port.out;

import com.phonghub.domain.model.RoomAssetEvent;
import java.util.List;
import java.util.UUID;

public interface RoomAssetEventRepositoryPort {

    RoomAssetEvent save(RoomAssetEvent event);

    List<RoomAssetEvent> findByAssetId(UUID assetId);
}
