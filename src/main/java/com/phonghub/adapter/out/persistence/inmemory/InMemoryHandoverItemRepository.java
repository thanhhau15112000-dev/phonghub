package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.HandoverItemRepositoryPort;
import com.phonghub.domain.model.HandoverItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryHandoverItemRepository implements HandoverItemRepositoryPort {

    private final Map<UUID, List<HandoverItem>> byHandover = new ConcurrentHashMap<>();

    @Override
    public void replaceAll(UUID handoverId, List<HandoverItem> items, UUID actorId) {
        byHandover.put(handoverId, new ArrayList<>(items));
    }

    @Override
    public List<HandoverItem> findByHandoverId(UUID handoverId) {
        return List.copyOf(byHandover.getOrDefault(handoverId, List.of()));
    }

    @Override
    public Optional<HandoverItem> findById(UUID id) {
        return byHandover.values().stream().flatMap(List::stream).filter(item -> item.id().equals(id)).findFirst();
    }

    @Override
    public HandoverItem save(HandoverItem item, UUID actorId) {
        List<HandoverItem> items = byHandover.computeIfAbsent(item.handoverId(), id -> new ArrayList<>());
        items.replaceAll(existing -> existing.id().equals(item.id()) ? item : existing);
        return item;
    }

    public void clear() {
        byHandover.clear();
    }
}
