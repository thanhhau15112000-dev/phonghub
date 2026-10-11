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
    @Transactional(readOnly = true)
    public TenantPage listTenants(UUID propertyId, String query, boolean includeHidden, int page, int size) {
        return delegate.listTenants(propertyId, query, includeHidden, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyTenantView getPropertyTenant(UUID propertyId, UUID tenantId) {
        return delegate.getPropertyTenant(propertyId, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PropertyTenantView addTenant(AddTenantCommand command) {
        return delegate.addTenant(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PropertyTenantView hideTenant(UUID propertyId, UUID tenantId) {
        return delegate.hideTenant(propertyId, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PropertyTenantView restoreTenant(UUID propertyId, UUID tenantId) {
        return delegate.restoreTenant(propertyId, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Tenant updateTenant(UpdateTenantCommand command) {
        return delegate.updateTenant(command);
    }
}
