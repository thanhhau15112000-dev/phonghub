package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.HandoverRepositoryPort;
import com.phonghub.domain.model.Handover;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryHandoverRepository implements HandoverRepositoryPort {

    private final Map<UUID, Handover> byContract = new ConcurrentHashMap<>();

    @Override
    public synchronized Handover save(Handover handover) {
        Handover existing = byContract.get(handover.contractId());
        if (existing != null && !existing.id().equals(handover.id())) {
            throw new IllegalStateException("A handover already exists for contract " + handover.contractId());
        }
        byContract.put(handover.contractId(), handover);
        return handover;
    }

    @Override
    public Optional<Handover> findByContractId(UUID contractId) {
        return Optional.ofNullable(byContract.get(contractId));
    }

    public void clear() {
        byContract.clear();
    }
}
