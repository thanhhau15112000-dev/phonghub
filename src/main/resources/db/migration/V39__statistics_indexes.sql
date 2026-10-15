-- Index cho truy vấn thống kê (#93): lọc theo nhà trọ + kỳ hóa đơn, phiếu bảo trì hoàn thành, tài khoản mới và
-- hoạt động theo thời gian.
CREATE INDEX IF NOT EXISTS idx_contracts_property ON contracts (property_id);

CREATE INDEX IF NOT EXISTS idx_invoices_period_active ON invoices (year, month, contract_id)
    WHERE status <> 'VOIDED' AND consolidated_into_invoice_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_maintenance_events_done ON maintenance_ticket_events (ticket_id, created_at)
    WHERE to_status IN ('RESOLVED', 'AWAITING_PAYMENT');

CREATE INDEX IF NOT EXISTS idx_maintenance_tickets_open ON maintenance_tickets (room_id, due_at)
    WHERE status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS');

CREATE INDEX IF NOT EXISTS idx_users_created_at ON users (created_at);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs (created_at);
