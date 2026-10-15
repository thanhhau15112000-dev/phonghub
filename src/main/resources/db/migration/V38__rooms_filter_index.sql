-- Lọc phòng theo nhà trọ + trạng thái + tầng + giá (#84).
CREATE INDEX idx_rooms_property_filter ON rooms (property_id, status, floor, base_price);
