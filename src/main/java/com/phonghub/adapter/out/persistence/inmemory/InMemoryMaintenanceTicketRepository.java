package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.MaintenanceTicketRepositoryPort;
import com.phonghub.domain.model.MaintenanceStatus;
import com.phonghub.domain.model.MaintenanceTicket;
import java.util.ArrayList;
import com.phonghub.domain.model.MaintenanceTicketEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMaintenanceTicketRepository implements MaintenanceTicketRepositoryPort {

    private final Map<UUID, MaintenanceTicket> store = new ConcurrentHashMap<>();
    private final Map<UUID, List<MaintenanceTicketEvent>> events = new ConcurrentHashMap<>();

    private MaintenanceTicket clone(MaintenanceTicket t) {
        if (t == null) return null;
        MaintenanceTicket copy = new MaintenanceTicket(
            t.getId(),
            t.getRoomId(),
            t.getPropertyId(),
            t.getRequestedByTenantId(),
            t.getAssignedTechnicianId(),
            t.getTitle(),
            t.getDescription(),
            t.getPriority(),
            t.getStatus(),
            t.getRepairCost(),
            t.getResolutionNotes(),
            t.getCauseCategory(),
            t.getLiableParty(),
            t.getRejectionReason(),
            t.getDueAt(),
            t.getSlaNotifiedAt(),
            t.getCreatedAt(),
            t.getUpdatedAt()
        );
        copy.restoreQuote(t.getQuote());
        return copy;
    }

    @Override
    public MaintenanceTicket save(MaintenanceTicket ticket) {
        events.computeIfAbsent(ticket.getId(), id -> new java.util.concurrent.CopyOnWriteArrayList<>())
            .addAll(ticket.drainEvents());
        MaintenanceTicket copy = clone(ticket);
        store.put(copy.getId(), copy);
        return clone(copy);
    }

    @Override
    public Optional<MaintenanceTicket> findById(UUID id) {
        return Optional.ofNullable(clone(store.get(id)));
    }

    @Override
    public List<MaintenanceTicket> findByPropertyId(UUID propertyId) {
        return store.values().stream()
            .filter(t -> t.getPropertyId().equals(propertyId))
            .map(this::clone)
            .toList();
    }

    @Override
    public List<MaintenanceTicket> findByRoomId(UUID roomId) {
        return store.values().stream()
            .filter(t -> t.getRoomId().equals(roomId))
            .map(this::clone)
            .toList();
    }

    @Override
    public List<MaintenanceTicket> findByRoomIdAndStatusNot(UUID roomId, MaintenanceStatus status) {
        return store.values().stream()
            .filter(t -> t.getRoomId().equals(roomId) && t.getStatus() != status)
            .map(this::clone)
            .toList();
    }

    @Override
    public List<MaintenanceTicket> findByAssignedTechnicianId(UUID technicianId) {
        return store.values().stream()
            .filter(t -> technicianId.equals(t.getAssignedTechnicianId()))
            .map(this::clone)
            .toList();
    }

    @Override
    public List<MaintenanceTicket> findAll() {
        return store.values().stream().map(this::clone).toList();
    }

    @Override
    public List<MaintenanceTicketEvent> findEvents(UUID ticketId) {
        return List.copyOf(events.getOrDefault(ticketId, List.of()));
    }

    public Map<UUID, MaintenanceTicket> snapshot() {
        Map<UUID, MaintenanceTicket> map = new java.util.HashMap<>();
        store.forEach((k, v) -> map.put(k, clone(v)));
        return map;
    }

    public void restore(Map<UUID, MaintenanceTicket> snapshot) {
        store.clear();
        if (snapshot != null) {
            snapshot.forEach((k, v) -> store.put(k, clone(v)));
        }
    }

    public void clear() {
        store.clear();
        events.clear();
    }
}
