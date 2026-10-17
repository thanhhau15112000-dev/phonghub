-- Biên bản bàn giao check-in (#62). Check-in diễn ra sau khi hợp đồng đã ACTIVE.
-- Luồng: DRAFT -> PENDING_TENANT -> PENDING_APPROVAL -> APPROVED hoặc REJECTED (staff/owner có thể mở lại).

ALTER TABLE handovers DROP CONSTRAINT IF EXISTS handovers_contract_id_key;

ALTER TABLE handovers
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'APPROVED'
        CONSTRAINT ck_handovers_status
        CHECK (status IN ('DRAFT', 'PENDING_TENANT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    ADD COLUMN contract_terms_hash CHAR(64),
    ADD COLUMN house_rules_text TEXT,
    ADD COLUMN house_rules_hash CHAR(64),
    ADD COLUMN rejection_reason TEXT,
    ADD COLUMN sent_at TIMESTAMPTZ,
    ADD COLUMN approved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN approved_at TIMESTAMPTZ,
    ADD COLUMN created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

-- Các dòng cũ (nếu có) đã được bàn giao thật nên giữ APPROVED; dòng mới bắt đầu ở DRAFT.
ALTER TABLE handovers ALTER COLUMN status SET DEFAULT 'DRAFT';

CREATE UNIQUE INDEX uk_handovers_contract_type ON handovers (contract_id, type);

CREATE TABLE handover_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    handover_id UUID NOT NULL REFERENCES handovers(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL
        CONSTRAINT ck_handover_photos_kind CHECK (kind IN ('ROOM', 'METER_ELECTRIC', 'METER_WATER')),
    image_url TEXT NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_handover_photos_handover ON handover_photos (handover_id);

CREATE TABLE handover_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    handover_id UUID NOT NULL REFERENCES handovers(id) ON DELETE CASCADE,
    asset_id UUID REFERENCES room_assets(id) ON DELETE SET NULL,
    asset_name VARCHAR(150) NOT NULL,
    quantity INT NOT NULL CONSTRAINT ck_handover_items_quantity CHECK (quantity > 0),
    status VARCHAR(20) NOT NULL
        CONSTRAINT ck_handover_items_status CHECK (status IN ('GOOD', 'DAMAGED', 'LOST', 'RETIRED')),
    condition_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_handover_items_handover ON handover_items (handover_id);

-- Nội quy chung của nhà trọ; biên bản lưu bản chụp và hash tại thời điểm gửi cho khách.
CREATE TABLE property_house_rules (
    property_id UUID PRIMARY KEY REFERENCES properties(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1 CONSTRAINT ck_property_house_rules_version CHECK (version > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL
);
