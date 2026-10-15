-- Nhắc hợp đồng sắp hết hạn (#87): mỗi (hợp đồng, ngày kết thúc) chỉ nhắc một lần; gia hạn đổi ngày kết thúc thì
-- được nhắc lại. Khóa chính chặn nhắc trùng khi job chạy song song.
CREATE TABLE contract_expiry_reminders (
    contract_id UUID NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
    end_date DATE NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (contract_id, end_date)
);

-- Job hết hạn tìm hợp đồng ACTIVE đã qua ngày kết thúc.
CREATE INDEX IF NOT EXISTS idx_contracts_active_end_date ON contracts (end_date) WHERE status = 'ACTIVE';
