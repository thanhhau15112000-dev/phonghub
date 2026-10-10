package com.phonghub.config;

import com.phonghub.application.port.in.TenantPrivacyUseCase;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalTenantPrivacyUseCase implements TenantPrivacyUseCase {

    private final TenantPrivacyUseCase delegate;

    public TransactionalTenantPrivacyUseCase(TenantPrivacyUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public PrivacyStatus status(UUID propertyId, UUID tenantId) {
        return delegate.status(propertyId, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrivacyStatus setLegalHold(UUID propertyId, UUID tenantId, String reason) {
        return delegate.setLegalHold(propertyId, tenantId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrivacyStatus clearLegalHold(UUID propertyId, UUID tenantId) {
        return delegate.clearLegalHold(propertyId, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ErasureOutcome requestErasure() {
        return delegate.requestErasure();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int purgeExpired(Instant now) {
        return delegate.purgeExpired(now);
    }
}
