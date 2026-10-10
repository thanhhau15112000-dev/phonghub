package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.TenantRepositoryPort;
import com.phonghub.domain.model.Tenant;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTenantRepository implements TenantRepositoryPort {

    private final Map<UUID, Tenant> store = new ConcurrentHashMap<>();
    /** tenantId -> (propertyId -> hiddenAt, null nếu hiển thị). */
    private final Map<UUID, Map<UUID, Optional<Instant>>> links = new ConcurrentHashMap<>();

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

    @Override
    public void linkToProperty(UUID tenantId, UUID propertyId, UUID actorId) {
        links.computeIfAbsent(tenantId, id -> new ConcurrentHashMap<>()).putIfAbsent(propertyId, Optional.empty());
    }

    @Override
    public Set<UUID> findLinkedPropertyIds(UUID tenantId) {
        return new HashSet<>(links.getOrDefault(tenantId, Map.of()).keySet());
    }

    @Override
    public List<PropertyLink> findLinks(UUID propertyId) {
        List<PropertyLink> result = new ArrayList<>();
        links.forEach((tenantId, byProperty) -> {
            Optional<Instant> hidden = byProperty.get(propertyId);
            if (hidden != null) {
                result.add(new PropertyLink(tenantId, hidden.orElse(null)));
            }
        });
        return result;
    }

    @Override
    public void setHidden(UUID tenantId, UUID propertyId, Instant hiddenAt, UUID actorId) {
        links.computeIfAbsent(tenantId, id -> new ConcurrentHashMap<>()).put(propertyId, Optional.ofNullable(hiddenAt));
    }

    @Override
    public List<Tenant> findAllByIds(Collection<UUID> ids) {
        return ids.stream().map(store::get).filter(java.util.Objects::nonNull).toList();
    }

    public Map<UUID, Tenant> snapshot() {
        return new java.util.HashMap<>(store);
    }

    public void restore(Map<UUID, Tenant> snapshot) {
        store.clear();
        if (snapshot != null) {
            store.putAll(snapshot);
        }
    }

    public void clear() {
        store.clear();
        links.clear();
    }
}
