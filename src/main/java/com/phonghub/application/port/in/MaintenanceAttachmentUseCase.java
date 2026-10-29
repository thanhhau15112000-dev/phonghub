package com.phonghub.application.port.in;

import com.phonghub.domain.model.MaintenanceAttachment;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Ảnh và một video ngắn đính kèm phiếu bảo trì, lưu ở kho riêng tư và xem qua đường dẫn có chữ ký. */
public interface MaintenanceAttachmentUseCase {

    MaintenanceAttachment upload(UUID ticketId, String contentType, byte[] content);

    /** Tải nhiều tệp trong một lượt: kiểm tra hết trước khi lưu, lỗi một tệp thì không lưu tệp nào. */
    List<MaintenanceAttachment> uploadAll(UUID ticketId, List<AttachmentFile> files);

    List<MaintenanceAttachment> list(UUID ticketId);

    URI signedUrl(UUID ticketId, UUID attachmentId);

    /** Nội dung tệp để server trả thẳng khi kho tệp không cấp được link http(s) (kho trong bộ nhớ ở docker/test). */
    AttachmentContent content(UUID ticketId, UUID attachmentId);

    void delete(UUID ticketId, UUID attachmentId);

    /** Id các tệp mà người dùng hiện tại xóa được (để giao diện chỉ hiện nút xóa đúng chỗ). */
    Set<UUID> deletableIds(UUID ticketId);

    /** Tệp tải lên kèm loại nội dung trình duyệt khai báo; nội dung thật được kiểm tra lại bằng magic bytes. */
    record AttachmentContent(String contentType, byte[] content) {}

    record AttachmentFile(String contentType, byte[] content) {}
}
