package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.HandoverItemRepositoryPort;
import com.phonghub.domain.model.HandoverItem;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryHandoverItemRepository implements HandoverItemRepositoryPort {

    private final Map<UUID, List<HandoverItem>> byHandover = new ConcurrentHashMap<>();

    @Override
    public void replaceAll(UUID handoverId, List<HandoverItem> items, UUID actorId) {
        byHandover.put(handoverId, List.copyOf(items));
    }

    @Override
    public List<HandoverItem> findByHandoverId(UUID handoverId) {
        return byHandover.getOrDefault(handoverId, List.of());
    }

    public void clear() {
        byHandover.clear();
    }
}
