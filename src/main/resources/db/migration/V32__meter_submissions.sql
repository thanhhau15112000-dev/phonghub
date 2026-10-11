-- Tenant tự gửi chỉ số điện nước kèm ảnh đồng hồ (#67 bổ sung). Server tự đối chiếu số khai với OCR;
-- owner/staff duyệt rồi mới thành chỉ số chính thức trong meter_readings.
CREATE TABLE meter_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    meter_type VARCHAR(20) NOT NULL CHECK (meter_type IN ('ELECTRIC', 'WATER')),
    declared_value NUMERIC(10, 2) NOT NULL CHECK (declared_value >= 0),
    photo_key TEXT NOT NULL,
    photo_content_type VARCHAR(50) NOT NULL,
    ocr_value NUMERIC(10, 2),
    ocr_confidence NUMERIC(4, 3),
    ocr_engine VARCHAR(50),
    match_status VARCHAR(12) NOT NULL CHECK (match_status IN ('MATCH', 'MISMATCH', 'UNVERIFIED')),
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    submitted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    review_note TEXT,
    final_value NUMERIC(10, 2),
    reading_id UUID REFERENCES meter_readings(id) ON DELETE SET NULL
);

CREATE INDEX idx_meter_submissions_room ON meter_submissions (room_id, submitted_at DESC);
-- Mỗi phòng và loại đồng hồ chỉ có một lượt gửi đang chờ duyệt, chặn cả khi gửi đồng thời.
CREATE UNIQUE INDEX uk_meter_submissions_pending ON meter_submissions (room_id, meter_type) WHERE status = 'PENDING';
