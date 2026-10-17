package com.phonghub.application.port.out;

import com.phonghub.domain.model.TenantPrivacy;
import java.util.Optional;
import java.util.UUID;

public interface TenantPrivacyRepositoryPort {

    Optional<TenantPrivacy> find(UUID tenantId);

    TenantPrivacy save(TenantPrivacy privacy);
}
