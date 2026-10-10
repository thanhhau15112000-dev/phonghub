package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.TenantPrivacyRepositoryPort;
import com.phonghub.domain.model.TenantPrivacy;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTenantPrivacyRepository implements TenantPrivacyRepositoryPort {

    private final Map<UUID, TenantPrivacy> store = new ConcurrentHashMap<>();

    @Override
    public Optional<TenantPrivacy> find(UUID tenantId) {
        return Optional.ofNullable(store.get(tenantId));
    }

    @Override
    public TenantPrivacy save(TenantPrivacy privacy) {
        store.put(privacy.tenantId(), privacy);
        return privacy;
    }

    public void clear() {
        store.clear();
    }
}
