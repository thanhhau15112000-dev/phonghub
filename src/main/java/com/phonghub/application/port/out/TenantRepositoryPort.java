package com.phonghub.application.port.out;

import com.phonghub.domain.model.Tenant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepositoryPort {
    Tenant save(Tenant tenant);
    Optional<Tenant> findById(UUID id);
    /** Serialize residency changes for an existing tenant within the caller's transaction. */
    default Optional<Tenant> findByIdForUpdate(UUID id) {
        return findById(id);
    }
    Optional<Tenant> findByUserId(UUID userId);
    Optional<Tenant> findByIdentityCardNumber(String idCard);
    List<Tenant> findAll();
}
