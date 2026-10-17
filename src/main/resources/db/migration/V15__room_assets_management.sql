-- Quản lý tài sản trong phòng (#60): mở rộng room_assets và thêm lịch sử sự kiện tài sản.

ALTER TABLE room_assets
    ADD COLUMN asset_code VARCHAR(40),
    ADD COLUMN image_url TEXT,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'GOOD'
        CONSTRAINT ck_room_assets_status CHECK (status IN ('GOOD', 'DAMAGED', 'LOST', 'RETIRED')),
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

WITH numbered AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS rn
    FROM room_assets
    WHERE asset_code IS NULL
)
UPDATE room_assets ra
SET asset_code = 'TS-' || lpad(n.rn::text, 6, '0')
FROM numbered n
WHERE ra.id = n.id AND ra.asset_code IS NULL;

ALTER TABLE room_assets ALTER COLUMN asset_code SET NOT NULL;
CREATE UNIQUE INDEX uk_room_assets_asset_code ON room_assets (asset_code);
CREATE INDEX idx_room_assets_room_active ON room_assets (room_id) WHERE deleted_at IS NULL;

CREATE TABLE room_asset_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES room_assets(id) ON DELETE CASCADE,
    event_type VARCHAR(20) NOT NULL
        CONSTRAINT ck_room_asset_events_type CHECK (event_type IN ('DAMAGED', 'REPAIRED', 'REPLACED', 'LOST_REPORTED')),
    description TEXT NOT NULL,
    cost NUMERIC(12, 2) NOT NULL DEFAULT 0 CONSTRAINT ck_room_asset_events_cost CHECK (cost >= 0),
    maintenance_ticket_id UUID REFERENCES maintenance_tickets(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_room_asset_events_asset ON room_asset_events (asset_id, created_at DESC);
