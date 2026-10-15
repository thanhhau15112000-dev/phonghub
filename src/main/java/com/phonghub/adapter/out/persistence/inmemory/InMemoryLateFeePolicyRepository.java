package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.LateFeePolicyRepositoryPort;
import com.phonghub.domain.model.LateFeePolicy;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryLateFeePolicyRepository implements LateFeePolicyRepositoryPort {

    private final Map<UUID, LateFeePolicy> store = new ConcurrentHashMap<>();

    @Override
    public Optional<LateFeePolicy> findByPropertyId(UUID propertyId) {
        return Optional.ofNullable(store.get(propertyId));
    }

    @Override
    public LateFeePolicy save(LateFeePolicy policy) {
        store.put(policy.propertyId(), policy);
        return policy;
    }

    public void clear() {
        store.clear();
    }
}
