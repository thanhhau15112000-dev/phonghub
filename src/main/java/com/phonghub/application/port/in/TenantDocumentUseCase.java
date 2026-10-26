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

    /** Giấy tờ của người thuê đang đăng nhập, cho Hồ sơ cá nhân (#244); không cần hợp đồng hay nhà trọ. */
    OwnDocuments ownDocuments();

    /**
     * Người thuê tự tải hoặc thay ảnh CCCD của mình ở Hồ sơ cá nhân (#244). Giấy tờ tra theo người thuê nên dùng
     * chung cho mọi nhà trọ và hợp đồng của họ, kể cả hợp đồng lập sau này.
     */
    DocumentSummary uploadOwnIdentityCard(DocumentFile front, DocumentFile back);

    /** {@code profileLinked} false: tài khoản người thuê chưa gắn hồ sơ có CCCD nên chưa tải được giấy tờ. */
    record OwnDocuments(boolean profileLinked, List<DocumentSummary> documents) {}

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
