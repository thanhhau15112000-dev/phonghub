-- Báo giá sửa chữa trước khi làm (#83). Người thuê xác nhận/không đạt dùng trạng thái VERIFIED đã có và timeline.
ALTER TABLE maintenance_tickets
    ADD COLUMN quote_amount NUMERIC(12, 2) CHECK (quote_amount IS NULL OR quote_amount >= 0),
    ADD COLUMN quote_note TEXT,
    ADD COLUMN quoted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN quoted_at TIMESTAMPTZ;
