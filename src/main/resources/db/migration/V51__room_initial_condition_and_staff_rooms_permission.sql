ALTER TABLE staff_property_assignments
    ADD COLUMN can_manage_rooms_and_assets BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE room_condition_snapshots (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    electric_meter NUMERIC(10, 2) NOT NULL CHECK (electric_meter >= 0),
    water_meter NUMERIC(10, 2) NOT NULL CHECK (water_meter >= 0),
    condition_note VARCHAR(2000) NOT NULL,
    electric_meter_photo_key VARCHAR(1024),
    water_meter_photo_key VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL REFERENCES users(id)
);

CREATE INDEX idx_room_condition_snapshots_latest
    ON room_condition_snapshots (room_id, created_at DESC, id DESC);

CREATE TABLE room_condition_items (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL REFERENCES room_condition_snapshots(id) ON DELETE CASCADE,
    asset_id UUID REFERENCES room_assets(id) ON DELETE SET NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    asset_name VARCHAR(150) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status VARCHAR(32) NOT NULL,
    condition_note VARCHAR(255),
    estimated_value NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (estimated_value >= 0),
    UNIQUE (snapshot_id, position)
);
