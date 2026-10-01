-- =============================================================================
-- V10: Add sepay_transactions table for SePay payment gateway webhook
-- =============================================================================

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
