-- =============================================================================
-- V13: Hóa đơn tháng có dòng chi tiết; phí sửa chữa chưa trả được gộp vào hóa đơn tháng
-- =============================================================================

-- 1. Dòng chi tiết gộp từ hóa đơn phí riêng (invoice_items có từ V1)
ALTER TABLE invoice_items
    ADD COLUMN IF NOT EXISTS source_invoice_id UUID REFERENCES invoices(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_invoice_items_source_invoice ON invoice_items(source_invoice_id);

-- 2. Hóa đơn phí đã được gộp vào hóa đơn tháng nào (NULL = còn thanh toán riêng)
ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS consolidated_into_invoice_id UUID REFERENCES invoices(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_invoices_consolidated_into ON invoices(consolidated_into_invoice_id);
