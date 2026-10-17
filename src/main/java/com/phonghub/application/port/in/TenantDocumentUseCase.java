package com.phonghub.application.port.in;

import com.phonghub.domain.model.DocumentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TenantDocumentUseCase {

    /** Xem danh sách che số, không có ảnh; mỗi lần gọi ghi một sự kiện audit. */
    List<DocumentSummary> list(UUID propertyId, UUID tenantId);

    DocumentSummary view(UUID propertyId, UUID tenantId, DocumentType type);

    /** Tải lên hoặc thay giấy tờ; ảnh được kiểm tra, xóa metadata rồi mã hóa trước khi lưu. */
    DocumentSummary upload(UploadDocumentCommand command);

    /** Xem đầy đủ số và ảnh sau khi người xem nhập lại mật khẩu của chính họ. */
    RevealedDocument reveal(UUID propertyId, UUID tenantId, DocumentType type, String password);

    void delete(UUID propertyId, UUID tenantId, DocumentType type);

    record DocumentFile(String contentType, byte[] content) {}

    record UploadDocumentCommand(
        UUID propertyId,
        UUID tenantId,
        DocumentType type,
        String number,
        DocumentFile front,
        DocumentFile back
    ) {}

    record DocumentSummary(
        DocumentType type,
        String maskedNumber,
        boolean hasFrontImage,
        boolean hasBackImage,
        Instant updatedAt
    ) {}

    record RevealedDocument(DocumentType type, String number, DocumentFile front, DocumentFile back) {}
}
