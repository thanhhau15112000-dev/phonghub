package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMaintenanceTicketRepository implements MaintenanceTicketRepositoryPort {

    private final Map<UUID, MaintenanceTicket> store = new ConcurrentHashMap<>();

    @Override
    public MaintenanceTicket save(MaintenanceTicket ticket) {
        store.put(ticket.getId(), ticket);
        return ticket;
    }

    @Override
    public Optional<MaintenanceTicket> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<MaintenanceTicket> findByPropertyId(UUID propertyId) {
        return store.values().stream()
            .filter(t -> t.getPropertyId().equals(propertyId))
            .toList();
    }

    @Override
    public List<MaintenanceTicket> findByRoomId(UUID roomId) {
        return store.values().stream()
            .filter(t -> t.getRoomId().equals(roomId))
            .toList();
    }

    @Override
    public List<MaintenanceTicket> findByRoomIdAndStatusNot(UUID roomId, MaintenanceStatus status) {
        return store.values().stream()
            .filter(t -> t.getRoomId().equals(roomId) && t.getStatus() != status)
            .toList();
    }

    @Override
    public List<MaintenanceTicket> findByAssignedTechnicianId(UUID technicianId) {
        return store.values().stream()
            .filter(t -> technicianId.equals(t.getAssignedTechnicianId()))
            .toList();
    }

    @Override
    public List<MaintenanceTicket> findAll() {
        return new ArrayList<>(store.values());
    }

    public void clear() {
        store.clear();
    }
}
