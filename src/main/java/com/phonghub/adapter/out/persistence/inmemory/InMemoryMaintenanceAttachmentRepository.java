package com.phonghub.adapter.out.persistence.inmemory;

import com.phonghub.application.port.out.MaintenanceAttachmentRepositoryPort;
import com.phonghub.domain.model.MaintenanceAttachment;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMaintenanceAttachmentRepository implements MaintenanceAttachmentRepositoryPort {

    private final Map<UUID, MaintenanceAttachment> store = new ConcurrentHashMap<>();

    @Override
    public synchronized MaintenanceAttachment save(MaintenanceAttachment attachment) {
        if (attachment.kind() == MaintenanceAttachment.Kind.VIDEO && store.values().stream().anyMatch(existing ->
            !existing.id().equals(attachment.id()) && existing.ticketId().equals(attachment.ticketId())
                && existing.kind() == MaintenanceAttachment.Kind.VIDEO)) {
            throw new com.phonghub.domain.exception.AttachmentLimitException("Phiếu đã có video");
        }
        store.put(attachment.id(), attachment);
        return attachment;
    }

    @Override
    public Optional<MaintenanceAttachment> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<MaintenanceAttachment> findByTicketId(UUID ticketId) {
        return store.values().stream().filter(a -> a.ticketId().equals(ticketId))
            .sorted(Comparator.comparing(MaintenanceAttachment::createdAt)).toList();
    }

    @Override
    public void delete(UUID id) {
        store.remove(id);
    }

    public void clear() {
        store.clear();
    }
}
