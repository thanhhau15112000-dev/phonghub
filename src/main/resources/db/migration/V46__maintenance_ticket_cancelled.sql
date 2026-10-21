-- #89: phiếu bảo trì chưa xử lý có thể bị hủy (CANCELLED) thay vì xóa; mốc hủy nằm trong maintenance_ticket_events.
ALTER TABLE maintenance_tickets DROP CONSTRAINT IF EXISTS maintenance_tickets_status_check;
ALTER TABLE maintenance_tickets ADD CONSTRAINT maintenance_tickets_status_check
    CHECK (status IN ('REPORTED', 'ASSIGNED', 'IN_PROGRESS', 'AWAITING_PAYMENT', 'RESOLVED', 'VERIFIED', 'REJECTED',
                      'CANCELLED'));
