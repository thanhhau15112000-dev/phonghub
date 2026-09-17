package com.phonghub.application.port.out;

import com.phonghub.domain.model.Property;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyRepositoryPort {
    Property save(Property property);
    Optional<Property> findById(UUID id);
    List<Property> findAll();
    List<Property> findAllById(Collection<UUID> ids);
    boolean existsById(UUID id);
    void deleteById(UUID id);
}
