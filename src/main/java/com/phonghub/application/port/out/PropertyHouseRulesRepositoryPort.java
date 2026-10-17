package com.phonghub.application.port.out;

import com.phonghub.domain.model.PropertyHouseRules;
import java.util.Optional;
import java.util.UUID;

public interface PropertyHouseRulesRepositoryPort {

    Optional<PropertyHouseRules> findByPropertyId(UUID propertyId);

    PropertyHouseRules save(PropertyHouseRules rules);
}
