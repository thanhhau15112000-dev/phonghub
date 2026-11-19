-- #250: nhật ký theo vai trò. Mỗi sự kiện được gắn thêm phòng, hợp đồng và người dùng liên quan
-- để lọc ở server cho nhân viên, kỹ thuật viên và người thuê (không nối bảng theo target_type lúc đọc).
ALTER TABLE audit_logs ADD COLUMN room_id UUID;
ALTER TABLE audit_logs ADD COLUMN contract_id UUID;
ALTER TABLE audit_logs ADD COLUMN subject_user_id UUID;

-- Bù dữ liệu cũ khi suy ra được từ đối tượng bị tác động.
UPDATE audit_logs a SET room_id = r.id, property_id = COALESCE(a.property_id, r.property_id)
FROM rooms r
WHERE a.target_type = 'ROOM' AND a.target_id ~ '^[0-9a-fA-F-]{36}$' AND r.id = a.target_id::uuid;

UPDATE audit_logs a SET contract_id = c.id, room_id = c.room_id, property_id = COALESCE(a.property_id, c.property_id)
FROM contracts c
WHERE a.target_type = 'CONTRACT' AND a.target_id ~ '^[0-9a-fA-F-]{36}$' AND c.id = a.target_id::uuid;

UPDATE audit_logs a SET contract_id = c.id, room_id = c.room_id, property_id = COALESCE(a.property_id, c.property_id)
FROM contract_occupants o JOIN contracts c ON c.id = o.contract_id
WHERE a.target_type = 'CONTRACT_OCCUPANT' AND a.target_id ~ '^[0-9a-fA-F-]{36}$' AND o.id = a.target_id::uuid;

UPDATE audit_logs a SET contract_id = c.id, room_id = c.room_id, property_id = COALESCE(a.property_id, c.property_id)
FROM handovers h JOIN contracts c ON c.id = h.contract_id
WHERE a.target_type = 'HANDOVER' AND a.target_id ~ '^[0-9a-fA-F-]{36}$' AND h.id = a.target_id::uuid;

UPDATE audit_logs a SET room_id = r.id, property_id = COALESCE(a.property_id, r.property_id)
FROM maintenance_tickets t JOIN rooms r ON r.id = t.room_id
WHERE a.target_type = 'MAINTENANCE_TICKET' AND a.target_id ~ '^[0-9a-fA-F-]{36}$' AND t.id = a.target_id::uuid;

UPDATE audit_logs a SET room_id = r.id, property_id = COALESCE(a.property_id, r.property_id)
FROM room_assets x JOIN rooms r ON r.id = x.room_id
WHERE a.target_type = 'ROOM_ASSET' AND a.target_id ~ '^[0-9a-fA-F-]{36}$' AND x.id = a.target_id::uuid;

UPDATE audit_logs a SET contract_id = i.contract_id, room_id = i.room_id, property_id = COALESCE(a.property_id, c.property_id)
FROM invoices i JOIN contracts c ON c.id = i.contract_id
WHERE a.target_type = 'INVOICE' AND a.target_id ~ '^[0-9a-fA-F-]{36}$' AND i.id = a.target_id::uuid;

UPDATE audit_logs a SET contract_id = p.contract_id, room_id = c.room_id, property_id = COALESCE(a.property_id, p.property_id)
FROM payment_claims p JOIN contracts c ON c.id = p.contract_id
WHERE a.target_type = 'PAYMENT_CLAIM' AND a.target_id ~ '^[0-9a-fA-F-]{36}$' AND p.id = a.target_id::uuid;

UPDATE audit_logs SET subject_user_id = target_id::uuid
WHERE target_type = 'USER' AND target_id ~ '^[0-9a-fA-F-]{36}$';

UPDATE audit_logs a SET subject_user_id = t.user_id
FROM tenants t
WHERE a.target_type IN ('TENANT', 'TENANT_DOCUMENT') AND a.target_id ~ '^[0-9a-fA-F-]{36}$'
  AND t.id = a.target_id::uuid AND t.user_id IS NOT NULL;

CREATE INDEX idx_audit_logs_contract_created ON audit_logs (contract_id, created_at DESC) WHERE contract_id IS NOT NULL;
CREATE INDEX idx_audit_logs_room_created ON audit_logs (room_id, created_at DESC) WHERE room_id IS NOT NULL;
CREATE INDEX idx_audit_logs_subject_created ON audit_logs (subject_user_id, created_at DESC) WHERE subject_user_id IS NOT NULL;
