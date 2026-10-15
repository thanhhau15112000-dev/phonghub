package com.phonghub.application.port.out;

import com.phonghub.domain.model.LateFeePolicy;
import java.util.Optional;
import java.util.UUID;

public interface LateFeePolicyRepositoryPort {
    Optional<LateFeePolicy> findByPropertyId(UUID propertyId);

    LateFeePolicy save(LateFeePolicy policy);
}
