-- Giao dịch SePay: phần tiền đã cộng vào hóa đơn, phần dư và lý do cần xem xét (#81).

ALTER TABLE sepay_transactions
    ADD COLUMN applied_amount NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (applied_amount >= 0),
    ADD COLUMN excess_amount NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (excess_amount >= 0),
    ADD COLUMN review_reason VARCHAR(30)
        CHECK (review_reason IN ('OVERPAID', 'INVOICE_NOT_PAYABLE', 'UNMATCHED'));

-- Giao dịch cũ: tiền vào đã khớp hóa đơn được coi là đã cộng đủ (trước đây không tách phần dư).
UPDATE sepay_transactions
SET applied_amount = transfer_amount
WHERE status = 'SUCCESS' AND invoice_id IS NOT NULL;

CREATE INDEX idx_sepay_transactions_review ON sepay_transactions (created_at DESC) WHERE review_reason IS NOT NULL;
