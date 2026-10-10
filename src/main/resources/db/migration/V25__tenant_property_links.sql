-- Quản lý tenant theo nhà trọ (#69).
-- Liên kết tenant với nhà trọ độc lập với hợp đồng để owner/staff thêm tenant trước khi lập hợp đồng,
-- và ẩn (xóa mềm) tenant theo từng nhà trọ. Tenant có hợp đồng tại nhà trọ vẫn thuộc nhà trọ đó dù không có dòng ở đây.
CREATE TABLE tenant_properties (
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    hidden_at TIMESTAMPTZ,
    hidden_by UUID REFERENCES users(id) ON DELETE SET NULL,
    PRIMARY KEY (tenant_id, property_id)
);

CREATE INDEX idx_tenant_properties_property ON tenant_properties (property_id);
