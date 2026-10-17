package com.phonghub.application.port.out;

import com.phonghub.domain.model.DocumentType;
import com.phonghub.domain.model.TenantDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantDocumentRepositoryPort {

    TenantDocument save(TenantDocument document);

    List<TenantDocument> findByTenantId(UUID tenantId);

    Optional<TenantDocument> findByTenantIdAndType(UUID tenantId, DocumentType type);

    boolean existsByTypeAndNumberHmacForOtherTenant(DocumentType type, String numberHmac, UUID tenantId);

    void delete(UUID tenantId, DocumentType type);
}
