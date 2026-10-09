package com.phonghub.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Hồ sơ giấy tờ của một người thuê. Ảnh được lưu ở kho riêng tư dưới dạng đã mã hóa
 * (front/backImageKey là khóa tệp, không phải link công khai); số GPLX luôn ở dạng mã hóa.
 */
public record TenantDocument(
    UUID id,
    UUID tenantId,
    UUID propertyId,
    DocumentType type,
    String numberCiphertext,
    String numberNonce,
    String numberHmac,
    String numberKeyId,
    String numberLast4,
    String frontImageKey,
    String frontContentType,
    String backImageKey,
    String backContentType,
    Instant createdAt,
    UUID createdBy,
    Instant updatedAt,
    UUID updatedBy
) {
    public TenantDocument {
        if (id == null || tenantId == null || propertyId == null || type == null) {
            throw new IllegalArgumentException("Document id, tenant, property and type are required");
        }
        if (frontImageKey == null || frontImageKey.isBlank() || frontContentType == null) {
            throw new IllegalArgumentException("Front image is required");
        }
        if (type == DocumentType.GPLX && (numberCiphertext == null || numberNonce == null
            || numberHmac == null || numberKeyId == null || numberLast4 == null)) {
            throw new IllegalArgumentException("Driving licence number is required");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public boolean hasBackImage() {
        return backImageKey != null && !backImageKey.isBlank();
    }

    public String maskedNumber() {
        return numberLast4 == null ? null : "****" + numberLast4;
    }
}
