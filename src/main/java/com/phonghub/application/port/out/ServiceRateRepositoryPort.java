package com.phonghub.application.port.out;

import com.phonghub.domain.model.ServiceRate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceRateRepositoryPort {

    ServiceRate save(ServiceRate rate);

    Optional<ServiceRate> findById(UUID id);

    List<ServiceRate> findByPropertyId(UUID propertyId);

    Optional<ServiceRate> findByPropertyIdAndName(UUID propertyId, String serviceName);
}
