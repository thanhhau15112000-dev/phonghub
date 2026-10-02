-- =============================================================================
-- PhongHub - Áp schema V12 (phí sửa chữa bảo trì) thủ công trên Supabase SQL Editor
-- =============================================================================
-- Yêu cầu: đã áp V10/V11 (apply_v10_v11_payment_schema.sql). Nội dung giống hệt
-- V12__maintenance_repair_fee_invoices.sql, chạy lại nhiều lần an toàn, và Flyway
-- chạy V12 sau này vẫn không lỗi.
-- =============================================================================

DO $$
BEGIN
    IF to_regclass('public.sepay_transactions') IS NULL OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'invoices' AND column_name = 'payment_code'
    ) THEN
        RAISE EXCEPTION 'Chưa áp V10/V11: chạy apply_v10_v11_payment_schema.sql trước.';
    END IF;
END $$;

-- 1. Phiếu bảo trì: nguyên nhân người báo khai, bên chịu phí chính thức, trạng thái chờ thanh toán
ALTER TABLE maintenance_tickets ADD COLUMN IF NOT EXISTS cause_category VARCHAR(30);
ALTER TABLE maintenance_tickets ADD COLUMN IF NOT EXISTS liable_party VARCHAR(20) NOT NULL DEFAULT 'OWNER';

ALTER TABLE maintenance_tickets DROP CONSTRAINT IF EXISTS maintenance_tickets_status_check;
ALTER TABLE maintenance_tickets ADD CONSTRAINT maintenance_tickets_status_check
    CHECK (status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS', 'AWAITING_PAYMENT', 'RESOLVED', 'VERIFIED', 'REJECTED'));

ALTER TABLE maintenance_tickets DROP CONSTRAINT IF EXISTS maintenance_tickets_cause_category_check;
ALTER TABLE maintenance_tickets ADD CONSTRAINT maintenance_tickets_cause_category_check
    CHECK (cause_category IS NULL OR cause_category IN ('NATURAL_WEAR', 'INFRASTRUCTURE', 'TENANT_USAGE', 'UNKNOWN'));

ALTER TABLE maintenance_tickets DROP CONSTRAINT IF EXISTS maintenance_tickets_liable_party_check;
ALTER TABLE maintenance_tickets ADD CONSTRAINT maintenance_tickets_liable_party_check
    CHECK (liable_party IN ('OWNER', 'TENANT', 'UNDETERMINED'));

-- 2. Hóa đơn: phân loại tiền thuê / phí bảo trì, gắn với phiếu bảo trì
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS invoice_type VARCHAR(20) NOT NULL DEFAULT 'RENT';
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS ticket_id UUID REFERENCES maintenance_tickets(id) ON DELETE SET NULL;

ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_invoice_type_check;
ALTER TABLE invoices ADD CONSTRAINT invoices_invoice_type_check CHECK (invoice_type IN ('RENT', 'MAINTENANCE'));

-- 3. Mỗi hợp đồng chỉ có một kỳ TIỀN THUÊ chưa hủy cho mỗi tháng (phí bảo trì không bị ràng buộc này)
DROP INDEX IF EXISTS uk_invoices_contract_period;
CREATE UNIQUE INDEX IF NOT EXISTS uk_invoices_rent_period
    ON invoices (contract_id, year, month) WHERE invoice_type = 'RENT' AND status <> 'VOIDED';

-- 4. Mỗi phiếu bảo trì chỉ có một khoản phí chưa hủy
CREATE UNIQUE INDEX IF NOT EXISTS uk_invoices_ticket
    ON invoices (ticket_id) WHERE ticket_id IS NOT NULL AND status <> 'VOIDED';

CREATE INDEX IF NOT EXISTS idx_invoices_ticket_id ON invoices(ticket_id);

-- Kiểm tra kết quả (mong đợi: 2, 2, 2, 1)
SELECT
    (SELECT count(*) FROM information_schema.columns WHERE table_name = 'maintenance_tickets' AND column_name IN ('cause_category', 'liable_party')) AS ticket_cols_ok_2,
    (SELECT count(*) FROM information_schema.columns WHERE table_name = 'invoices' AND column_name IN ('invoice_type', 'ticket_id')) AS invoice_cols_ok_2,
    (SELECT count(*) FROM pg_indexes WHERE indexname IN ('uk_invoices_rent_period', 'uk_invoices_ticket')) AS indexes_ok_2,
    (SELECT count(*) FROM pg_indexes WHERE indexname = 'uk_invoices_contract_period') AS old_index_gone_0;
