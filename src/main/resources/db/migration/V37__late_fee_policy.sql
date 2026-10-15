-- Phạt trả trễ cấu hình theo nhà trọ (#80). Mỗi nhà trọ tối đa một chính sách; chưa có dòng nghĩa là không phạt.
CREATE TABLE property_late_fee_policies (
    property_id UUID PRIMARY KEY REFERENCES properties(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    fee_type VARCHAR(20) NOT NULL DEFAULT 'PERCENT' CHECK (fee_type IN ('PERCENT', 'FIXED')),
    fee_value NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (fee_value >= 0),
    grace_days INT NOT NULL DEFAULT 0 CHECK (grace_days BETWEEN 0 AND 365),
    cap_amount NUMERIC(14, 2) CHECK (cap_amount IS NULL OR cap_amount >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    CHECK (fee_type <> 'PERCENT' OR fee_value <= 100)
);

-- Quét hóa đơn tiền thuê quá hạn mỗi giờ.
CREATE INDEX idx_invoices_overdue_rent ON invoices (due_date)
    WHERE invoice_type = 'RENT' AND status IN ('ISSUED', 'PARTIALLY_PAID', 'OVERDUE');
