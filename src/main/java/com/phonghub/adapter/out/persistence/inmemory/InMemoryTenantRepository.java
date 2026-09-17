package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.model.Tenant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTenantRepository implements TenantRepositoryPort {

    private final Map<UUID, Tenant> store = new ConcurrentHashMap<>();

    @Override
    public Tenant save(Tenant tenant) {
        store.put(tenant.id(), tenant);
        return tenant;
    }

    @Override
    public Optional<Tenant> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Tenant> findByUserId(UUID userId) {
        if (userId == null) return Optional.empty();
        return store.values().stream()
            .filter(t -> userId.equals(t.userId()))
            .findFirst();
    }

    @Override
    public Optional<Tenant> findByIdentityCardNumber(String idCard) {
        if (idCard == null) return Optional.empty();
        return store.values().stream()
            .filter(t -> idCard.trim().equalsIgnoreCase(t.identityCardNumber().trim()))
            .findFirst();
    }

    @Override
    public List<Tenant> findAll() {
        return new ArrayList<>(store.values());
    }

    public void clear() {
        store.clear();
    }
}
