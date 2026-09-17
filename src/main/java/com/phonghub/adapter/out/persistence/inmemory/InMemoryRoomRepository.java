package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRoomRepository implements RoomRepositoryPort {

    private final Map<UUID, Room> store = new ConcurrentHashMap<>();

    private Room clone(Room r) {
        if (r == null) return null;
        return new Room(
            r.getId(),
            r.getPropertyId(),
            r.getRoomNumber(),
            r.getFloor(),
            r.getAreaSqm(),
            r.getBasePrice(),
            r.getMaxOccupants(),
            r.getStatus(),
            r.getCreatedAt(),
            r.getUpdatedAt()
        );
    }

    @Override
    public Room save(Room room) {
        Room copy = clone(room);
        store.put(copy.getId(), copy);
        return clone(copy);
    }

    @Override
    public Optional<Room> findById(UUID id) {
        return Optional.ofNullable(clone(store.get(id)));
    }

    @Override
    public List<Room> findByPropertyId(UUID propertyId) {
        return store.values().stream()
            .filter(r -> r.getPropertyId().equals(propertyId))
            .map(this::clone)
            .toList();
    }

    @Override
    public List<Room> findByPropertyIdAndStatus(UUID propertyId, RoomStatus status) {
        return store.values().stream()
            .filter(r -> r.getPropertyId().equals(propertyId) && r.getStatus() == status)
            .map(this::clone)
            .toList();
    }

    @Override
    public Optional<Room> findByPropertyIdAndRoomNumber(UUID propertyId, String roomNumber) {
        return store.values().stream()
            .filter(r -> r.getPropertyId().equals(propertyId) && r.getRoomNumber().equalsIgnoreCase(roomNumber.trim()))
            .map(this::clone)
            .findFirst();
    }

    @Override
    public List<Room> findAllById(Collection<UUID> ids) {
        return ids.stream()
            .map(store::get)
            .filter(java.util.Objects::nonNull)
            .map(this::clone)
            .toList();
    }

    @Override
    public boolean existsById(UUID id) {
        return store.containsKey(id);
    }

    public Map<UUID, Room> snapshot() {
        Map<UUID, Room> map = new java.util.HashMap<>();
        store.forEach((k, v) -> map.put(k, clone(v)));
        return map;
    }

    public void restore(Map<UUID, Room> snapshot) {
        store.clear();
        if (snapshot != null) {
            snapshot.forEach((k, v) -> store.put(k, clone(v)));
        }
    }

    public void clear() {
        store.clear();
    }
}
