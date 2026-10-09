package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.HandoverPhotoRepositoryPort;
import com.phonghub.domain.model.HandoverPhoto;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryHandoverPhotoRepository implements HandoverPhotoRepositoryPort {

    private final Map<UUID, HandoverPhoto> store = new ConcurrentHashMap<>();

    @Override
    public HandoverPhoto save(HandoverPhoto photo) {
        store.put(photo.id(), photo);
        return photo;
    }

    @Override
    public Optional<HandoverPhoto> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<HandoverPhoto> findByHandoverId(UUID handoverId) {
        return store.values().stream().filter(photo -> photo.handoverId().equals(handoverId))
            .sorted(Comparator.comparing(HandoverPhoto::createdAt)).toList();
    }

    @Override
    public void delete(UUID id) {
        store.remove(id);
    }

    public void clear() {
        store.clear();
    }
}
