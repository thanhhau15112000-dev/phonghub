package com.phonghub.config;

import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.application.port.out.CurrentUser;
import com.phonghub.application.port.out.CurrentUserPort;
import com.phonghub.domain.model.Property;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Cache danh sách nhà trọ theo người dùng (#88); mọi thao tác ghi nhà trọ xóa cache. */
public class CachingPropertyUseCase implements PropertyUseCase {

    private final PropertyUseCase delegate;
    private final PhongHubCaches caches;
    private final CurrentUserPort currentUserPort;

    public CachingPropertyUseCase(PropertyUseCase delegate, PhongHubCaches caches, CurrentUserPort currentUserPort) {
        this.delegate = delegate;
        this.caches = caches;
        this.currentUserPort = currentUserPort;
    }

    @Override
    public List<Property> listAccessibleProperties() {
        CurrentUser user = currentUserPort.getCurrentUser();
        if (user == null) {
            return delegate.listAccessibleProperties();
        }
        return caches.get(new PhongHubCaches.Key("properties", user.id(), null), delegate::listAccessibleProperties);
    }

    @Override
    public Property getProperty(UUID propertyId) {
        return delegate.getProperty(propertyId);
    }

    @Override
    public Property createProperty(CreatePropertyCommand command) {
        return write(() -> delegate.createProperty(command));
    }

    @Override
    public Property verifyProperty(UUID propertyId) {
        return write(() -> delegate.verifyProperty(propertyId));
    }

    @Override
    public Property rejectProperty(UUID propertyId, String reason) {
        return write(() -> delegate.rejectProperty(propertyId, reason));
    }

    @Override
    public Property updateProperty(UpdatePropertyCommand command) {
        return write(() -> delegate.updateProperty(command));
    }

    @Override
    public List<Property> listArchivedProperties() {
        return delegate.listArchivedProperties();
    }

    @Override
    public Property archiveProperty(UUID propertyId) {
        return write(() -> delegate.archiveProperty(propertyId));
    }

    @Override
    public Property restoreProperty(UUID propertyId) {
        return write(() -> delegate.restoreProperty(propertyId));
    }

    /** PropertyService không chạy trong transaction nên xóa cache cả khi lỗi (có thể đã ghi một phần). */
    private <T> T write(Supplier<T> action) {
        try {
            return action.get();
        } finally {
            caches.evictAll();
        }
    }
}
