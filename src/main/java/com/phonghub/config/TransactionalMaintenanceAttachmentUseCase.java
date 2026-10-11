package com.phonghub.config;

import com.phonghub.application.port.in.MaintenanceAttachmentUseCase;
import com.phonghub.domain.model.MaintenanceAttachment;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalMaintenanceAttachmentUseCase implements MaintenanceAttachmentUseCase {

    private final MaintenanceAttachmentUseCase delegate;

    public TransactionalMaintenanceAttachmentUseCase(MaintenanceAttachmentUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceAttachment upload(UUID ticketId, String contentType, byte[] content) {
        return delegate.upload(ticketId, contentType, content);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaintenanceAttachment> list(UUID ticketId) {
        return delegate.list(ticketId);
    }

    @Override
    @Transactional(readOnly = true)
    public URI signedUrl(UUID ticketId, UUID attachmentId) {
        return delegate.signedUrl(ticketId, attachmentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID ticketId, UUID attachmentId) {
        delegate.delete(ticketId, attachmentId);
    }
}
