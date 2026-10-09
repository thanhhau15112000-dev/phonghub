package com.phonghub.config;

import com.phonghub.application.port.in.HandoverUseCase;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverPhoto;
import com.phonghub.domain.model.HandoverPhotoKind;
import com.phonghub.domain.model.PropertyHouseRules;
import java.net.URI;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalHandoverUseCase implements HandoverUseCase {

    private final HandoverUseCase delegate;

    public TransactionalHandoverUseCase(HandoverUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Handover create(HandoverCommand command) {
        return delegate.create(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Handover update(HandoverCommand command) {
        return delegate.update(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HandoverPhoto addPhoto(UUID contractId, HandoverPhotoKind kind, String contentType, byte[] content) {
        return delegate.addPhoto(contractId, kind, contentType, content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePhoto(UUID contractId, UUID photoId) {
        delegate.removePhoto(contractId, photoId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Handover send(UUID contractId) {
        return delegate.send(contractId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Handover confirm(UUID contractId) {
        return delegate.confirm(contractId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Handover approve(UUID contractId) {
        return delegate.approve(contractId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Handover reject(UUID contractId, String reason) {
        return delegate.reject(contractId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Handover reopen(UUID contractId) {
        return delegate.reopen(contractId);
    }

    @Override
    @Transactional(readOnly = true)
    public HandoverDetails get(UUID contractId) {
        return delegate.get(contractId);
    }

    @Override
    @Transactional(readOnly = true)
    public URI photoSignedUrl(UUID contractId, UUID photoId) {
        return delegate.photoSignedUrl(contractId, photoId);
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyHouseRules getHouseRules(UUID propertyId) {
        return delegate.getHouseRules(propertyId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PropertyHouseRules saveHouseRules(UUID propertyId, String content) {
        return delegate.saveHouseRules(propertyId, content);
    }
}
