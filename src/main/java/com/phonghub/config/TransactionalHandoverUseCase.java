package com.phonghub.config;

import com.phonghub.application.port.in.HandoverUseCase;
import com.phonghub.domain.model.Handover;
import com.phonghub.domain.model.HandoverItem;
import com.phonghub.domain.model.HandoverPhoto;
import com.phonghub.domain.model.HandoverPhotoKind;
import com.phonghub.domain.model.HandoverType;
import com.phonghub.domain.model.PropertyHouseRules;
import java.math.BigDecimal;
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
    public Handover create(HandoverType type, HandoverCommand command) {
        return delegate.create(type, command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Handover update(HandoverType type, HandoverCommand command) {
        return delegate.update(type, command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HandoverPhoto addPhoto(HandoverType type, UUID contractId, HandoverPhotoKind kind, String contentType,
                                  byte[] content) {
        return delegate.addPhoto(type, contractId, kind, contentType, content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePhoto(HandoverType type, UUID contractId, UUID photoId) {
        delegate.removePhoto(type, contractId, photoId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HandoverItem updateItem(HandoverType type, UUID contractId, UUID itemId, ItemCommand command) {
        return delegate.updateItem(type, contractId, itemId, command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Handover send(HandoverType type, UUID contractId) {
        return delegate.send(type, contractId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Handover confirm(HandoverType type, UUID contractId) {
        return delegate.confirm(type, contractId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Handover approve(HandoverType type, UUID contractId, BigDecimal finalDeduction) {
        return delegate.approve(type, contractId, finalDeduction);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Handover skip(HandoverType type, UUID contractId) {
        return delegate.skip(type, contractId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Handover reject(HandoverType type, UUID contractId, String reason) {
        return delegate.reject(type, contractId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Handover reopen(HandoverType type, UUID contractId) {
        return delegate.reopen(type, contractId);
    }

    @Override
    @Transactional(readOnly = true)
    public HandoverDetails get(HandoverType type, UUID contractId) {
        return delegate.get(type, contractId);
    }

    @Override
    @Transactional(readOnly = true)
    public URI photoSignedUrl(HandoverType type, UUID contractId, UUID photoId) {
        return delegate.photoSignedUrl(type, contractId, photoId);
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
