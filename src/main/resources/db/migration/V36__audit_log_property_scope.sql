-- Nhật ký kiểm toán theo phạm vi nhà trọ (#78): owner chỉ xem sự kiện thuộc nhà trọ của mình.
-- Sự kiện cũ được gán property_id theo đối tượng bị tác động khi suy ra được.
ALTER TABLE audit_logs ADD COLUMN property_id UUID REFERENCES properties(id) ON DELETE SET NULL;

UPDATE audit_logs SET property_id = target_id::uuid
WHERE target_type = 'PROPERTY' AND target_id ~ '^[0-9a-fA-F-]{36}$'
  AND EXISTS (SELECT 1 FROM properties p WHERE p.id = target_id::uuid);

UPDATE audit_logs a SET property_id = r.property_id
FROM rooms r
WHERE a.property_id IS NULL AND a.target_type = 'ROOM' AND a.target_id ~ '^[0-9a-fA-F-]{36}$'
  AND r.id = a.target_id::uuid;

UPDATE audit_logs a SET property_id = c.property_id
FROM contracts c
WHERE a.property_id IS NULL AND a.target_type = 'CONTRACT' AND a.target_id ~ '^[0-9a-fA-F-]{36}$'
  AND c.id = a.target_id::uuid;

UPDATE audit_logs a SET property_id = r.property_id
FROM maintenance_tickets t JOIN rooms r ON r.id = t.room_id
WHERE a.property_id IS NULL AND a.target_type = 'MAINTENANCE_TICKET' AND a.target_id ~ '^[0-9a-fA-F-]{36}$'
  AND t.id = a.target_id::uuid;

CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_property_created ON audit_logs (property_id, created_at DESC);
CREATE INDEX idx_audit_logs_actor_created ON audit_logs (actor_id, created_at DESC);
