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

    @Override
    public Contract save(Contract contract) {
        store.put(contract.getId(), contract);
        return contract;
    }

    @Override
    public Optional<Contract> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Contract> findByPropertyId(UUID propertyId) {
        return store.values().stream()
            .filter(c -> c.getPropertyId().equals(propertyId))
            .toList();
    }

    @Override
    public List<Contract> findByRoomId(UUID roomId) {
        return store.values().stream()
            .filter(c -> c.getRoomId().equals(roomId))
            .toList();
    }

    @Override
    public Optional<Contract> findActiveByRoomId(UUID roomId) {
        return store.values().stream()
            .filter(c -> c.getRoomId().equals(roomId) && c.getStatus() == ContractStatus.ACTIVE)
            .findFirst();
    }

    @Override
    public List<Contract> findByPrimaryTenantId(UUID tenantId) {
        return store.values().stream()
            .filter(c -> c.getPrimaryTenantId().equals(tenantId))
            .toList();
    }

    @Override
    public List<Contract> findByOccupantTenantId(UUID tenantId) {
        return store.values().stream()
            .filter(c -> c.getOccupants().stream().anyMatch(o -> o.tenantId().equals(tenantId)))
            .toList();
    }

    @Override
    public List<Contract> findAll() {
        return new ArrayList<>(store.values());
    }

    public void clear() {
        store.clear();
    }
}
