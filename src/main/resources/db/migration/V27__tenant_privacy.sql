-- Quyền riêng tư và lưu trữ dữ liệu tenant (#71).
-- Khóa hồ sơ sau check-out được suy ra từ trạng thái hợp đồng; bảng này chỉ giữ trạng thái không suy ra được:
-- cờ legal_hold chặn xóa, yêu cầu xóa sớm của tenant và thời điểm đã xóa dữ liệu giấy tờ.
CREATE TABLE tenant_privacy (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    legal_hold BOOLEAN NOT NULL DEFAULT FALSE,
    legal_hold_reason TEXT,
    legal_hold_set_by UUID REFERENCES users(id) ON DELETE SET NULL,
    legal_hold_set_at TIMESTAMPTZ,
    erasure_requested_at TIMESTAMPTZ,
    purged_at TIMESTAMPTZ,
    CONSTRAINT ck_tenant_privacy_hold_reason CHECK (
        NOT legal_hold OR (legal_hold_reason IS NOT NULL AND length(btrim(legal_hold_reason)) > 0))
);
