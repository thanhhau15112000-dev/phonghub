package com.phonghub.config;

import com.phonghub.application.port.in.MaintenanceAttachmentUseCase;
import com.phonghub.application.port.in.MaintenanceSubmissionUseCase;
import com.phonghub.application.port.in.MaintenanceUseCase;
import com.phonghub.domain.model.MaintenanceTicket;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalMaintenanceSubmissionUseCase implements MaintenanceSubmissionUseCase {

    private final MaintenanceSubmissionUseCase delegate;

    public TransactionalMaintenanceSubmissionUseCase(MaintenanceSubmissionUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceTicket createTicketWithAttachments(
        MaintenanceUseCase.CreateMaintenanceTicketCommand command,
        List<MaintenanceAttachmentUseCase.AttachmentFile> files
    ) {
        return delegate.createTicketWithAttachments(command, files);
    }
}
