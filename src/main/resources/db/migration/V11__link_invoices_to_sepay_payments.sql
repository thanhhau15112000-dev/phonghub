-- =============================================================================
-- V11: Kỳ thanh toán tiền thuê (bảng invoices có từ V1) + khớp giao dịch SePay
-- =============================================================================

-- 1. Mã nội dung chuyển khoản cho từng kỳ ("PH" + 8 ký tự) và thời điểm thanh toán đủ
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS payment_code VARCHAR(20);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uk_invoices_payment_code
    ON invoices (payment_code) WHERE payment_code IS NOT NULL;

-- 2. Mỗi hợp đồng chỉ có một kỳ chưa hủy cho mỗi tháng.
-- Index này được V12 thay bằng uk_invoices_rent_period (chỉ áp cho hóa đơn RENT). Chỉ tạo khi V12
-- chưa chạy (chưa có cột invoice_type): nếu V12 đã được áp tay trước thì tạo lại sẽ lỗi vì dữ liệu
-- hợp lệ có hóa đơn tiền thuê và phí bảo trì cùng tháng.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'invoices' AND column_name = 'invoice_type'
    ) THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uk_invoices_contract_period
            ON invoices (contract_id, year, month) WHERE status <> 'VOIDED';
    END IF;
END $$;

-- 3. Liên kết giao dịch SePay với kỳ thanh toán đã khớp
ALTER TABLE sepay_transactions
    ADD COLUMN IF NOT EXISTS invoice_id UUID REFERENCES invoices(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_sepay_transactions_invoice_id ON sepay_transactions(invoice_id);
