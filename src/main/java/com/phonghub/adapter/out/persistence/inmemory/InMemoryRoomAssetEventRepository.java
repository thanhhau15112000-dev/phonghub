package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.RoomAssetEventRepositoryPort;
import com.phonghub.domain.model.RoomAssetEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRoomAssetEventRepository implements RoomAssetEventRepositoryPort {

    private final Map<UUID, RoomAssetEvent> store = new ConcurrentHashMap<>();

    @Override
    public RoomAssetEvent save(RoomAssetEvent event) {
        store.put(event.id(), event);
        return event;
    }

    @Override
    public List<RoomAssetEvent> findByAssetId(UUID assetId) {
        return store.values().stream()
            .filter(event -> event.assetId().equals(assetId))
            .sorted(Comparator.comparing(RoomAssetEvent::createdAt).reversed())
            .toList();
    }

    public void clear() {
        store.clear();
    }
}
