package com.phonghub.application.port.in;

import com.phonghub.domain.model.MaintenanceAttachment;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Ảnh và một video ngắn đính kèm phiếu bảo trì, lưu ở kho riêng tư và xem qua đường dẫn có chữ ký. */
public interface MaintenanceAttachmentUseCase {

    MaintenanceAttachment upload(UUID ticketId, String contentType, byte[] content);

    List<MaintenanceAttachment> list(UUID ticketId);

    URI signedUrl(UUID ticketId, UUID attachmentId);

    void delete(UUID ticketId, UUID attachmentId);
}
