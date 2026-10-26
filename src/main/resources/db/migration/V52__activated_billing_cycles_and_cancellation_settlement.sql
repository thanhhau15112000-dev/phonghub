-- Store activation and deposit-settlement state without rewriting previously issued invoices.
ALTER TABLE contracts
    ADD COLUMN IF NOT EXISTS activated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deposit_applied_amount NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (deposit_applied_amount >= 0),
    ADD COLUMN IF NOT EXISTS settlement_credit_amount NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (settlement_credit_amount >= 0);

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS cycle_start_date DATE,
    ADD COLUMN IF NOT EXISTS cycle_end_date DATE,
    ADD COLUMN IF NOT EXISTS cycle_number INT NOT NULL DEFAULT 0 CHECK (cycle_number >= 0),
    ADD COLUMN IF NOT EXISTS deposit_paid_amount NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (deposit_paid_amount >= 0 AND deposit_paid_amount <= paid_amount),
    ADD COLUMN IF NOT EXISTS settlement_credit_amount NUMERIC(12, 2) NOT NULL DEFAULT 0
        CHECK (settlement_credit_amount >= 0);

-- Legacy calendar-month invoices keep their original months and dates.
UPDATE invoices
SET cycle_start_date = make_date(year, month, 1),
    cycle_end_date = (make_date(year, month, 1) + INTERVAL '1 month - 1 day')::DATE
WHERE invoice_type = 'RENT'
  AND (cycle_start_date IS NULL OR cycle_end_date IS NULL);

ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_invoice_type_check;
ALTER TABLE invoices ADD CONSTRAINT invoices_invoice_type_check
    CHECK (invoice_type IN ('RENT', 'MAINTENANCE', 'CANCELLATION'));

CREATE INDEX IF NOT EXISTS idx_invoices_contract_cycle_start
    ON invoices (contract_id, cycle_start_date)
    WHERE invoice_type IN ('RENT', 'CANCELLATION');

CREATE UNIQUE INDEX IF NOT EXISTS uk_invoices_cancellation_period
    ON invoices (contract_id, year, month)
    WHERE invoice_type = 'CANCELLATION' AND status <> 'VOIDED';
