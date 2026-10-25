-- #205: xác nhận thanh toán thủ công. Người thuê báo đã thanh toán, người có quyền thu tiền xác nhận hoặc từ chối;
-- người thu tiền cũng ghi nhận trực tiếp. Xác nhận cộng tiền vào hóa đơn qua cùng đường với webhook.
CREATE TABLE payment_claims (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    contract_id UUID NOT NULL REFERENCES contracts(id),
    property_id UUID NOT NULL REFERENCES properties(id),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    method VARCHAR(20) NOT NULL CHECK (method IN ('BANK_TRANSFER', 'CASH')),
    note VARCHAR(1000),
    source VARCHAR(20) NOT NULL CHECK (source IN ('TENANT_CLAIM', 'MANAGER_RECORD')),
    status VARCHAR(12) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    decided_by UUID REFERENCES users(id) ON DELETE SET NULL,
    decided_at TIMESTAMPTZ,
    reject_reason VARCHAR(500),
    CHECK ((status = 'PENDING' AND decided_at IS NULL) OR (status <> 'PENDING' AND decided_at IS NOT NULL)),
    CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL)
);

CREATE INDEX idx_payment_claims_pending ON payment_claims (property_id, created_at) WHERE status = 'PENDING';
CREATE INDEX idx_payment_claims_invoice ON payment_claims (invoice_id);
CREATE INDEX idx_payment_claims_contract ON payment_claims (contract_id, created_at DESC);
-- Mỗi hóa đơn chỉ có một yêu cầu đang chờ xác nhận.
CREATE UNIQUE INDEX uk_payment_claims_pending_invoice ON payment_claims (invoice_id) WHERE status = 'PENDING';
