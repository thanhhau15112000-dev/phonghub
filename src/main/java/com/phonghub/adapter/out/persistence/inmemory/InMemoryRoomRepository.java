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

    @Override
    public Room save(Room room) {
        store.put(room.getId(), room);
        return room;
    }

    @Override
    public Optional<Room> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Room> findByPropertyId(UUID propertyId) {
        return store.values().stream()
            .filter(r -> r.getPropertyId().equals(propertyId))
            .toList();
    }

    @Override
    public List<Room> findByPropertyIdAndStatus(UUID propertyId, RoomStatus status) {
        return store.values().stream()
            .filter(r -> r.getPropertyId().equals(propertyId) && r.getStatus() == status)
            .toList();
    }

    @Override
    public Optional<Room> findByPropertyIdAndRoomNumber(UUID propertyId, String roomNumber) {
        return store.values().stream()
            .filter(r -> r.getPropertyId().equals(propertyId) && r.getRoomNumber().equalsIgnoreCase(roomNumber.trim()))
            .findFirst();
    }

    @Override
    public List<Room> findAllById(Collection<UUID> ids) {
        return ids.stream()
            .map(store::get)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    @Override
    public boolean existsById(UUID id) {
        return store.containsKey(id);
    }

    public void clear() {
        store.clear();
    }
}
