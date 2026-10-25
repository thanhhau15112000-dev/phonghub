-- #222: retain early check-out intent, issue final utility invoices, and ledger deposit offsets.
ALTER TABLE handovers
    ADD COLUMN early_termination BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN termination_reason VARCHAR(500);

ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_invoice_type_check;
ALTER TABLE invoices ADD CONSTRAINT invoices_invoice_type_check
    CHECK (invoice_type IN ('RENT', 'MAINTENANCE', 'CHECK_OUT'));
CREATE UNIQUE INDEX IF NOT EXISTS uk_invoices_checkout_contract
    ON invoices (contract_id) WHERE invoice_type = 'CHECK_OUT' AND status <> 'VOIDED';

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_payment_type_check;
ALTER TABLE payments ADD CONSTRAINT payments_payment_type_check
    CHECK (payment_type IN ('DEPOSIT', 'INVOICE', 'REFUND', 'DEPOSIT_OFFSET'));
ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_payment_method_check;
ALTER TABLE payments ADD CONSTRAINT payments_payment_method_check
    CHECK (payment_method IN ('BANK_TRANSFER', 'CASH', 'MOMO', 'VNPAY', 'DEPOSIT_OFFSET'));
ALTER TABLE payments ADD COLUMN recorded_by UUID REFERENCES users(id) ON DELETE SET NULL;
