-- Hồ sơ giấy tờ tenant (#61): ảnh giấy tờ được mã hóa trước khi lưu vào kho riêng tư,
-- số GPLX được mã hóa AES-GCM kèm HMAC để chống trùng. Số CCCD vẫn nằm ở tenants (V14).

CREATE TABLE tenant_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE RESTRICT,
    document_type VARCHAR(10) NOT NULL
        CONSTRAINT ck_tenant_documents_type CHECK (document_type IN ('CCCD', 'GPLX')),
    number_ciphertext TEXT,
    number_nonce TEXT,
    number_hmac CHAR(64),
    number_key_id VARCHAR(64),
    number_last4 VARCHAR(4),
    front_image_url TEXT NOT NULL,
    front_content_type VARCHAR(50) NOT NULL,
    back_image_url TEXT,
    back_content_type VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uk_tenant_documents_tenant_type UNIQUE (tenant_id, document_type),
    CONSTRAINT ck_tenant_documents_number CHECK (
        document_type = 'CCCD'
        OR (number_ciphertext IS NOT NULL AND number_nonce IS NOT NULL AND number_hmac IS NOT NULL
            AND number_key_id IS NOT NULL AND number_last4 IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_tenant_documents_number_hmac ON tenant_documents (document_type, number_hmac)
    WHERE number_hmac IS NOT NULL;
CREATE INDEX idx_tenant_documents_property ON tenant_documents (property_id);
