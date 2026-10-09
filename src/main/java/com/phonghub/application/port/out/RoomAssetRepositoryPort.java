package com.phonghub.application.port.out;

import com.phonghub.domain.model.RoomAsset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomAssetRepositoryPort {

    RoomAsset save(RoomAsset asset);

    Optional<RoomAsset> findById(UUID id);

    List<RoomAsset> findActiveByRoomId(UUID roomId);

    boolean existsByAssetCode(String assetCode);
}
