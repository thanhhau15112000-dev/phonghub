package com.phonghub.application.port.in;

import com.phonghub.domain.model.MaintenanceTicket;
import java.util.List;

/** Tạo phiếu báo sự cố cùng ảnh/video đính kèm trong một giao dịch: lỗi tệp nào thì không tạo phiếu. */
public interface MaintenanceSubmissionUseCase {

    MaintenanceTicket createTicketWithAttachments(
        MaintenanceUseCase.CreateMaintenanceTicketCommand command,
        List<MaintenanceAttachmentUseCase.AttachmentFile> files
    );
}
