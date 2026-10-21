package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.RoomRepositoryPort;
import com.phonghub.domain.model.Room;
import com.phonghub.domain.model.RoomPage;
import com.phonghub.domain.model.RoomSearchCriteria;
import com.phonghub.domain.model.RoomStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRoomRepository implements RoomRepositoryPort {

    private final Map<UUID, Room> store = new ConcurrentHashMap<>();

    private Room clone(Room r) {
        if (r == null) return null;
        Room copy = new Room(
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
        copy.restoreArchivedAt(r.getArchivedAt());
        return copy;
    }

    @Override
    public Room save(Room room) {
        Room copy = clone(room);
        store.put(copy.getId(), copy);
        return clone(copy);
    }

    @Override
    public RoomPage search(RoomSearchCriteria c) {
        List<Room> matches = store.values().stream()
            .filter(r -> r.getPropertyId().equals(c.propertyId()))
            .filter(r -> r.isArchived() == c.archived())
            .filter(r -> c.status() == null || r.getStatus() == c.status())
            .filter(r -> c.minPrice() == null || r.getBasePrice().compareTo(c.minPrice()) >= 0)
            .filter(r -> c.maxPrice() == null || r.getBasePrice().compareTo(c.maxPrice()) <= 0)
            .filter(r -> c.floor() == null || r.getFloor() == c.floor())
            .filter(r -> c.restrictToRoomIds() == null || c.restrictToRoomIds().contains(r.getId()))
            .sorted(Comparator.comparingInt(Room::getFloor).thenComparing(Room::getRoomNumber))
            .toList();
        int from = c.size() == null ? 0 : Math.min(matches.size(), c.page() * c.size());
        int to = c.size() == null ? matches.size() : Math.min(matches.size(), from + c.size());
        return new RoomPage(matches.subList(from, to), matches.size(), c.page(), c.size());
    }

    @Override
    public Optional<Room> findById(UUID id) {
        return Optional.ofNullable(clone(store.get(id)));
    }

    @Override
    public List<Room> findByPropertyId(UUID propertyId) {
        return store.values().stream()
            .filter(r -> r.getPropertyId().equals(propertyId) && !r.isArchived())
            .map(this::clone)
            .toList();
    }

    @Override
    public List<Room> findByPropertyIdAndStatus(UUID propertyId, RoomStatus status) {
        return store.values().stream()
            .filter(r -> r.getPropertyId().equals(propertyId) && r.getStatus() == status && !r.isArchived())
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
