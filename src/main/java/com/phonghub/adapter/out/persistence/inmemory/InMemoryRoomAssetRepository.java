package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.RoomAssetRepositoryPort;
import com.phonghub.domain.model.RoomAsset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRoomAssetRepository implements RoomAssetRepositoryPort {

    private final Map<UUID, RoomAsset> store = new ConcurrentHashMap<>();

    @Override
    public RoomAsset save(RoomAsset asset) {
        store.put(asset.id(), asset);
        return asset;
    }

    @Override
    public Optional<RoomAsset> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<RoomAsset> findActiveByRoomId(UUID roomId) {
        return store.values().stream()
            .filter(asset -> asset.roomId().equals(roomId) && !asset.isDeleted())
            .sorted(Comparator.comparing(RoomAsset::createdAt).thenComparing(RoomAsset::name))
            .toList();
    }

    @Override
    public boolean existsByAssetCode(String assetCode) {
        return store.values().stream().anyMatch(asset -> asset.assetCode().equalsIgnoreCase(assetCode));
    }

    public void clear() {
        store.clear();
    }
}
