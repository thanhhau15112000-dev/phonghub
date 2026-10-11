package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.HandoverRepositoryPort;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverType;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryHandoverRepository implements HandoverRepositoryPort {

    private record Key(UUID contractId, HandoverType type) {}

    private final Map<Key, Handover> store = new ConcurrentHashMap<>();

    @Override
    public synchronized Handover save(Handover handover) {
        Key key = new Key(handover.contractId(), handover.type());
        Handover existing = store.get(key);
        if (existing != null && !existing.id().equals(handover.id())) {
            throw new IllegalStateException("A " + handover.type() + " handover already exists for contract "
                + handover.contractId());
        }
        store.put(key, handover);
        return handover;
    }

    @Override
    public Optional<Handover> find(UUID contractId, HandoverType type) {
        return Optional.ofNullable(store.get(new Key(contractId, type)));
    }

    public void clear() {
        store.clear();
    }
}
