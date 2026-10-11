-- SLA và timeline phiếu bảo trì (#74).
-- due_at: hạn xử lý suy ra từ mức ưu tiên lúc tạo phiếu; sla_notified_at: đã báo quá hạn (mỗi phiếu một lần).
ALTER TABLE maintenance_tickets
    ADD COLUMN due_at TIMESTAMPTZ,
    ADD COLUMN sla_notified_at TIMESTAMPTZ;

-- Phiếu cũ: dùng hạn mặc định của từng mức ưu tiên (khớp cấu hình mặc định của ứng dụng).
UPDATE maintenance_tickets SET due_at = created_at + CASE priority
    WHEN 'URGENT' THEN INTERVAL '4 hours'
    WHEN 'HIGH' THEN INTERVAL '24 hours'
    WHEN 'MEDIUM' THEN INTERVAL '72 hours'
    ELSE INTERVAL '168 hours' END;

-- Phiếu cũ đã quá hạn nhưng chưa xong sẽ không bị báo hàng loạt khi triển khai: coi như đã báo.
UPDATE maintenance_tickets SET sla_notified_at = CURRENT_TIMESTAMP
WHERE due_at < CURRENT_TIMESTAMP AND status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS');

CREATE TABLE maintenance_ticket_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES maintenance_tickets(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_maintenance_ticket_events_ticket ON maintenance_ticket_events (ticket_id, created_at);
