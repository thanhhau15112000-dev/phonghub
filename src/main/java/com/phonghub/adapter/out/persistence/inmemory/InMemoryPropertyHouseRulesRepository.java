package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.PropertyHouseRulesRepositoryPort;
import com.phonghub.domain.model.PropertyHouseRules;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPropertyHouseRulesRepository implements PropertyHouseRulesRepositoryPort {

    private final Map<UUID, PropertyHouseRules> store = new ConcurrentHashMap<>();

    @Override
    public Optional<PropertyHouseRules> findByPropertyId(UUID propertyId) {
        return Optional.ofNullable(store.get(propertyId));
    }

    @Override
    public PropertyHouseRules save(PropertyHouseRules rules) {
        store.put(rules.propertyId(), rules);
        return rules;
    }

    public void clear() {
        store.clear();
    }
}
