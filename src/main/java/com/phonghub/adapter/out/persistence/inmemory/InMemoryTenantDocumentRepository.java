package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.TenantDocumentRepositoryPort;
import com.phonghub.domain.model.DocumentType;
import com.phonghub.domain.model.TenantDocument;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTenantDocumentRepository implements TenantDocumentRepositoryPort {

    private record Key(UUID tenantId, DocumentType type) {}

    private final Map<Key, TenantDocument> store = new ConcurrentHashMap<>();

    @Override
    public TenantDocument save(TenantDocument document) {
        store.put(new Key(document.tenantId(), document.type()), document);
        return document;
    }

    @Override
    public List<TenantDocument> findByTenantId(UUID tenantId) {
        return store.values().stream().filter(document -> document.tenantId().equals(tenantId)).toList();
    }

    @Override
    public Optional<TenantDocument> findByTenantIdAndType(UUID tenantId, DocumentType type) {
        return Optional.ofNullable(store.get(new Key(tenantId, type)));
    }

    @Override
    public boolean existsByTypeAndNumberHmacForOtherTenant(DocumentType type, String numberHmac, UUID tenantId) {
        return store.values().stream().anyMatch(document -> document.type() == type
            && numberHmac.equals(document.numberHmac()) && !document.tenantId().equals(tenantId));
    }

    @Override
    public void delete(UUID tenantId, DocumentType type) {
        store.remove(new Key(tenantId, type));
    }

    public void clear() {
        store.clear();
    }
}
