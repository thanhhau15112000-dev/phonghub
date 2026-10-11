package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.ServiceRateRepositoryPort;
import com.phonghub.domain.model.ServiceRate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryServiceRateRepository implements ServiceRateRepositoryPort {

    private final Map<UUID, ServiceRate> store = new ConcurrentHashMap<>();

    @Override
    public ServiceRate save(ServiceRate rate) {
        store.put(rate.id(), rate);
        return rate;
    }

    @Override
    public Optional<ServiceRate> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<ServiceRate> findByPropertyId(UUID propertyId) {
        return store.values().stream().filter(rate -> rate.propertyId().equals(propertyId))
            .sorted(Comparator.comparing(ServiceRate::serviceName)).toList();
    }

    @Override
    public Optional<ServiceRate> findByPropertyIdAndName(UUID propertyId, String serviceName) {
        return store.values().stream().filter(rate -> rate.propertyId().equals(propertyId)
            && rate.serviceName().equalsIgnoreCase(serviceName)).findFirst();
    }

    public void clear() {
        store.clear();
    }
}
