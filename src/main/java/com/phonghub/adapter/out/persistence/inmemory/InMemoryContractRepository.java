package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.ContractRepositoryPort;
import com.phonghub.domain.model.Contract;
import com.phonghub.domain.model.ContractStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryContractRepository implements ContractRepositoryPort {

    private final Map<UUID, Contract> store = new ConcurrentHashMap<>();

    private Contract clone(Contract c) {
        if (c == null) return null;
        return new Contract(
            c.getId(),
            c.getPropertyId(),
            c.getRoomId(),
            c.getPrimaryTenantId(),
            c.getDepositAmount(),
            c.getRentAmount(),
            c.getStartDate(),
            c.getEndDate(),
            c.getPaymentDay(),
            c.getStatus(),
            c.getOccupants() != null ? new ArrayList<>(c.getOccupants()) : new ArrayList<>(),
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }

    @Override
    public Contract save(Contract contract) {
        Contract copy = clone(contract);
        store.put(copy.getId(), copy);
        return clone(copy);
    }

    @Override
    public Optional<Contract> findById(UUID id) {
        return Optional.ofNullable(clone(store.get(id)));
    }

    @Override
    public List<Contract> findByPropertyId(UUID propertyId) {
        return store.values().stream()
            .filter(c -> c.getPropertyId().equals(propertyId))
            .map(this::clone)
            .toList();
    }

    @Override
    public List<Contract> findByRoomId(UUID roomId) {
        return store.values().stream()
            .filter(c -> c.getRoomId().equals(roomId))
            .map(this::clone)
            .toList();
    }

    @Override
    public Optional<Contract> findActiveByRoomId(UUID roomId) {
        return store.values().stream()
            .filter(c -> c.getRoomId().equals(roomId) && c.getStatus() == ContractStatus.ACTIVE)
            .map(this::clone)
            .findFirst();
    }

    @Override
    public List<Contract> findByPrimaryTenantId(UUID tenantId) {
        return store.values().stream()
            .filter(c -> c.getPrimaryTenantId().equals(tenantId))
            .map(this::clone)
            .toList();
    }

    @Override
    public List<Contract> findByOccupantTenantId(UUID tenantId) {
        return store.values().stream()
            .filter(c -> c.getOccupants().stream().anyMatch(o -> o.tenantId().equals(tenantId)))
            .map(this::clone)
            .toList();
    }

    @Override
    public List<Contract> findAll() {
        return store.values().stream().map(this::clone).toList();
    }

    public Map<UUID, Contract> snapshot() {
        Map<UUID, Contract> map = new java.util.HashMap<>();
        store.forEach((k, v) -> map.put(k, clone(v)));
        return map;
    }

    public void restore(Map<UUID, Contract> snapshot) {
        store.clear();
        if (snapshot != null) {
            snapshot.forEach((k, v) -> store.put(k, clone(v)));
        }
    }

    public void clear() {
        store.clear();
    }
}
