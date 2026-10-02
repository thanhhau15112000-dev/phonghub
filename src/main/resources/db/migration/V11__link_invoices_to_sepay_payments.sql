-- =============================================================================
-- V11: Kỳ thanh toán tiền thuê (bảng invoices có từ V1) + khớp giao dịch SePay
-- =============================================================================

-- 1. Mã nội dung chuyển khoản cho từng kỳ ("PH" + 8 ký tự) và thời điểm thanh toán đủ
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS payment_code VARCHAR(20);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uk_invoices_payment_code
    ON invoices (payment_code) WHERE payment_code IS NOT NULL;

-- 2. Mỗi hợp đồng chỉ có một kỳ chưa hủy cho mỗi tháng
CREATE UNIQUE INDEX IF NOT EXISTS uk_invoices_contract_period
    ON invoices (contract_id, year, month) WHERE status <> 'VOIDED';

-- 3. Liên kết giao dịch SePay với kỳ thanh toán đã khớp
ALTER TABLE sepay_transactions
    ADD COLUMN IF NOT EXISTS invoice_id UUID REFERENCES invoices(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_sepay_transactions_invoice_id ON sepay_transactions(invoice_id);
