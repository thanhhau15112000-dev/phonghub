package com.phonghub.config;

import com.phonghub.application.port.in.TenantUseCase;
import com.phonghub.domain.model.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalTenantUseCase implements TenantUseCase {

    private final TenantUseCase delegate;

    public TransactionalTenantUseCase(TenantUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tenant> getTenant(UUID tenantId) {
        return delegate.getTenant(tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Tenant updateTenant(UpdateTenantCommand command) {
        return delegate.updateTenant(command);
    }
}
