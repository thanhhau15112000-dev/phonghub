ALTER TABLE sepay_transactions
    ADD COLUMN IF NOT EXISTS gateway_transaction_id VARCHAR(160);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sepay_transactions_gateway_transaction_id
    ON sepay_transactions(gateway_transaction_id)
    WHERE gateway_transaction_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS sepay_gateway_orders (
    order_invoice_number VARCHAR(32) PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PAID', 'EXPIRED')),
    transaction_id VARCHAR(160) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_sepay_gateway_orders_invoice_id
    ON sepay_gateway_orders(invoice_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sepay_gateway_orders_pending_invoice
    ON sepay_gateway_orders(invoice_id)
    WHERE status = 'PENDING';
