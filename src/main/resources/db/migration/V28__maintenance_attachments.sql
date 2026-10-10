-- Đính kèm ảnh/video khi báo hỏng (#73). Tệp nằm ở kho riêng tư; bảng chỉ giữ khóa tệp và metadata.
CREATE TABLE maintenance_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES maintenance_tickets(id) ON DELETE CASCADE,
    kind VARCHAR(10) NOT NULL CONSTRAINT ck_maintenance_attachments_kind CHECK (kind IN ('IMAGE', 'VIDEO')),
    storage_key TEXT NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    uploaded_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_maintenance_attachments_ticket ON maintenance_attachments (ticket_id);
-- Tối đa một video cho mỗi phiếu; chặn cả khi hai yêu cầu tải lên cùng lúc.
CREATE UNIQUE INDEX uk_maintenance_attachments_one_video ON maintenance_attachments (ticket_id) WHERE kind = 'VIDEO';
