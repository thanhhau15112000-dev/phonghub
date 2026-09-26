package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.PropertyRepositoryPort;
import com.phonghub.domain.model.Property;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPropertyRepository implements PropertyRepositoryPort {

    private final Map<UUID, Property> store = new ConcurrentHashMap<>();

    @Override
    public Property save(Property property) {
        store.put(property.id(), property);
        return property;
    }

    @Override
    public Optional<Property> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Property> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Property> findAllById(Collection<UUID> ids) {
        return ids.stream()
            .map(store::get)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    @Override
    public List<Property> findByOwnerId(UUID ownerId) {
        if (ownerId == null) {
            return List.of();
        }
        return store.values().stream()
            .filter(p -> ownerId.equals(p.ownerId()))
            .toList();
    }

    @Override
    public boolean existsById(UUID id) {
        return store.containsKey(id);
    }

    @Override
    public void deleteById(UUID id) {
        store.remove(id);
    }

    public void clear() {
        store.clear();
    }
}
