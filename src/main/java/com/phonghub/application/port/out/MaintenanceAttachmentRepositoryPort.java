package com.phonghub.application.port.out;

import com.phonghub.domain.model.MaintenanceAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceAttachmentRepositoryPort {

    MaintenanceAttachment save(MaintenanceAttachment attachment);

    Optional<MaintenanceAttachment> findById(UUID id);

    List<MaintenanceAttachment> findByTicketId(UUID ticketId);

    void delete(UUID id);
}
