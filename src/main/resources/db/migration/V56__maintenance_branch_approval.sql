-- #202/#203: chốt nhánh hao mòn / lỗi người thuê, duyệt trước khi sửa, báo giá nhiều phiên bản.
ALTER TABLE maintenance_tickets
    ADD COLUMN branch_confirmed_at TIMESTAMPTZ,
    ADD COLUMN branch_confirmed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN approval_due_at TIMESTAMPTZ,
    ADD COLUMN owner_approved_at TIMESTAMPTZ,
    ADD COLUMN approval_escalated_at TIMESTAMPTZ,
    ADD COLUMN quote_due_at TIMESTAMPTZ,
    ADD COLUMN quote_approved_at TIMESTAMPTZ,
    ADD COLUMN approved_quote_total NUMERIC(12, 2) CHECK (approved_quote_total IS NULL OR approved_quote_total >= 0),
    ADD COLUMN negotiation_rounds INT NOT NULL DEFAULT 0 CHECK (negotiation_rounds BETWEEN 0 AND 2),
    ADD COLUMN quote_version INT NOT NULL DEFAULT 0 CHECK (quote_version >= 0),
    ADD COLUMN resume_status VARCHAR(32),
    ADD COLUMN approval_reminder_sent_at TIMESTAMPTZ,
    ADD COLUMN requote_requested BOOLEAN NOT NULL DEFAULT FALSE;

-- Phiếu của người thuê đã giao hoặc đang sửa trước khi có bước chốt nhánh coi như đã chốt và đã duyệt, để không bị chặn giữa chừng.
UPDATE maintenance_tickets
SET branch_confirmed_at = COALESCE(assigned_at, created_at),
    quote_approved_at = CASE WHEN liable_party = 'TENANT' THEN COALESCE(assigned_at, created_at) END
WHERE requested_by_tenant_id IS NOT NULL AND status IN ('ASSIGNED', 'IN_PROGRESS');

ALTER TABLE maintenance_tickets DROP CONSTRAINT IF EXISTS maintenance_tickets_status_check;
ALTER TABLE maintenance_tickets ADD CONSTRAINT maintenance_tickets_status_check
    CHECK (status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS', 'AWAITING_PAYMENT', 'RESOLVED', 'VERIFIED', 'REJECTED',
                      'CANCELLED', 'PENDING_OWNER_APPROVAL', 'PENDING_TENANT_APPROVAL'));

-- Job quá hạn chỉ quét các phiếu đang chờ duyệt.
CREATE INDEX idx_maintenance_tickets_pending_approval
    ON maintenance_tickets (status, approval_due_at, quote_due_at)
    WHERE status IN ('PENDING_OWNER_APPROVAL', 'PENDING_TENANT_APPROVAL');

CREATE TABLE maintenance_quote_versions (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES maintenance_tickets(id) ON DELETE CASCADE,
    version INT NOT NULL CHECK (version > 0),
    labor_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (labor_amount >= 0),
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount > 0),
    note TEXT,
    quoted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    quoted_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'NEGOTIATED', 'EXPIRED')),
    tenant_note TEXT,
    decided_at TIMESTAMPTZ,
    UNIQUE (ticket_id, version)
);

CREATE TABLE maintenance_quote_lines (
    quote_id UUID NOT NULL REFERENCES maintenance_quote_versions(id) ON DELETE CASCADE,
    position INT NOT NULL CHECK (position >= 0),
    name VARCHAR(200) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    PRIMARY KEY (quote_id, position)
);
