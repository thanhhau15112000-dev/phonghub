package com.phonghub.config;

import com.phonghub.application.port.in.MaintenanceAttachmentUseCase;
import com.phonghub.domain.model.MaintenanceAttachment;
import java.net.URI;
import java.util.List;
import java.util.Set;
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
    @Transactional(rollbackFor = Exception.class)
    public List<MaintenanceAttachment> uploadAll(UUID ticketId, List<AttachmentFile> files) {
        return delegate.uploadAll(ticketId, files);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MaintenanceAttachment> uploadAll(UUID ticketId, List<AttachmentFile> files, MaintenanceAttachment.Phase phase) {
        return delegate.uploadAll(ticketId, files, phase);
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
    @Transactional(readOnly = true)
    public AttachmentContent content(UUID ticketId, UUID attachmentId) {
        return delegate.content(ticketId, attachmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> deletableIds(UUID ticketId) {
        return delegate.deletableIds(ticketId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID ticketId, UUID attachmentId) {
        delegate.delete(ticketId, attachmentId);
    }
}
