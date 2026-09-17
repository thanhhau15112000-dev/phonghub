package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.StaffPropertyAssignmentPort;
import com.phonghub.domain.model.StaffPropertyAssignment;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryStaffPropertyAssignmentRepository implements StaffPropertyAssignmentPort {

    private final Map<UUID, StaffPropertyAssignment> store = new ConcurrentHashMap<>();

    @Override
    public StaffPropertyAssignment save(StaffPropertyAssignment assignment) {
        store.put(assignment.id(), assignment);
        return assignment;
    }

    @Override
    public Set<UUID> findPropertyIdsByUserId(UUID userId) {
        return store.values().stream()
            .filter(a -> a.staffUserId().equals(userId))
            .map(StaffPropertyAssignment::propertyId)
            .collect(Collectors.toSet());
    }

    @Override
    public List<StaffPropertyAssignment> findByUserId(UUID userId) {
        return store.values().stream()
            .filter(a -> a.staffUserId().equals(userId))
            .toList();
    }

    @Override
    public List<StaffPropertyAssignment> findByPropertyId(UUID propertyId) {
        return store.values().stream()
            .filter(a -> a.propertyId().equals(propertyId))
            .toList();
    }

    @Override
    public boolean isUserAssignedToProperty(UUID userId, UUID propertyId) {
        return store.values().stream()
            .anyMatch(a -> a.staffUserId().equals(userId) && a.propertyId().equals(propertyId));
    }

    @Override
    public void deleteByUserIdAndPropertyId(UUID userId, UUID propertyId) {
        store.values().removeIf(a -> a.staffUserId().equals(userId) && a.propertyId().equals(propertyId));
    }

    public void clear() {
        store.clear();
    }
}
