package com.phonghub.config;

import com.phonghub.application.port.in.TenantDocumentUseCase;
import com.phonghub.domain.model.DocumentType;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalTenantDocumentUseCase implements TenantDocumentUseCase {

    private final TenantDocumentUseCase delegate;

    public TransactionalTenantDocumentUseCase(TenantDocumentUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DocumentSummary> list(UUID propertyId, UUID tenantId) {
        return delegate.list(propertyId, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentSummary view(UUID propertyId, UUID tenantId, DocumentType type) {
        return delegate.view(propertyId, tenantId, type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentSummary upload(UploadDocumentCommand command) {
        return delegate.upload(command);
    }

    /**
     * Không bọc giao dịch: lần nhập sai mật khẩu phải được ghi audit và đếm lại dù có ném lỗi,
     * nếu giao dịch rollback thì sự kiện audit thất bại sẽ bị mất.
     */
    @Override
    public RevealedDocument reveal(UUID propertyId, UUID tenantId, DocumentType type, String password) {
        return delegate.reveal(propertyId, tenantId, type, password);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID propertyId, UUID tenantId, DocumentType type) {
        delegate.delete(propertyId, tenantId, type);
    }
}
