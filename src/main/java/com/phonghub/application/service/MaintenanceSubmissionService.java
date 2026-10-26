package com.phonghub.application.service;

import com.phonghub.application.port.in.MaintenanceAttachmentUseCase;
import com.phonghub.application.port.in.MaintenanceSubmissionUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.domain.model.MaintenanceTicket;
import java.util.List;

public class MaintenanceSubmissionService implements MaintenanceSubmissionUseCase {

    private final MaintenanceUseCase maintenanceUseCase;
    private final MaintenanceAttachmentUseCase attachmentUseCase;

    public MaintenanceSubmissionService(MaintenanceUseCase maintenanceUseCase, MaintenanceAttachmentUseCase attachmentUseCase) {
        this.maintenanceUseCase = maintenanceUseCase;
        this.attachmentUseCase = attachmentUseCase;
    }

    @Override
    public MaintenanceTicket createTicketWithAttachments(
        MaintenanceUseCase.CreateMaintenanceTicketCommand command,
        List<MaintenanceAttachmentUseCase.AttachmentFile> files
    ) {
        MaintenanceTicket ticket = maintenanceUseCase.createTicket(command);
        if (files != null && !files.isEmpty()) {
            attachmentUseCase.uploadAll(ticket.getId(), files);
        }
        return ticket;
    }
}
