-- =============================================================================
-- PhongHub - Áp schema thanh toán (V10 + V11) thủ công trên Supabase SQL Editor
-- =============================================================================
-- Dùng khi cần seed dữ liệu trước khi deploy bản code có V10/V11.
-- Nội dung giống hệt V10__add_sepay_transactions_table.sql và
-- V11__link_invoices_to_sepay_payments.sql, mọi lệnh đều IF NOT EXISTS, nên khi app
-- deploy sau này Flyway chạy lại V10/V11 vẫn không lỗi.
-- =============================================================================

DO $$
BEGIN
    IF to_regclass('public.contracts') IS NULL OR to_regclass('public.invoices') IS NULL THEN
        RAISE EXCEPTION 'Thiếu bảng contracts/invoices: DB chưa chạy migration V1-V9. Cần deploy app (profile prod) ít nhất một lần trước.';
    END IF;
END $$;

-- ---------------------------- V10 ----------------------------
CREATE TABLE IF NOT EXISTS sepay_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sepay_id BIGINT NOT NULL UNIQUE,
    gateway VARCHAR(100) NOT NULL,
    transaction_date VARCHAR(50),
    account_number VARCHAR(50) NOT NULL,
    sub_account VARCHAR(100),
    transfer_type VARCHAR(20) NOT NULL DEFAULT 'in',
    transfer_amount NUMERIC(14, 2) NOT NULL,
    accumulated NUMERIC(14, 2),
    code VARCHAR(100),
    content TEXT,
    reference_code VARCHAR(100),
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
    contract_id UUID REFERENCES contracts(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sepay_transactions_sepay_id ON sepay_transactions(sepay_id);
CREATE INDEX IF NOT EXISTS idx_sepay_transactions_contract_id ON sepay_transactions(contract_id);
CREATE INDEX IF NOT EXISTS idx_sepay_transactions_created_at ON sepay_transactions(created_at DESC);

-- ---------------------------- V11 ----------------------------
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS payment_code VARCHAR(20);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uk_invoices_payment_code
    ON invoices (payment_code) WHERE payment_code IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_invoices_contract_period
    ON invoices (contract_id, year, month) WHERE status <> 'VOIDED';

ALTER TABLE sepay_transactions
    ADD COLUMN IF NOT EXISTS invoice_id UUID REFERENCES invoices(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_sepay_transactions_invoice_id ON sepay_transactions(invoice_id);

-- Kiểm tra kết quả
SELECT
    (SELECT count(*) FROM information_schema.columns
      WHERE table_name = 'invoices' AND column_name IN ('payment_code', 'paid_at')) AS invoice_cols_ok_2,
    (SELECT count(*) FROM information_schema.columns
      WHERE table_name = 'sepay_transactions' AND column_name = 'invoice_id') AS sepay_invoice_col_ok_1;
