-- #244: người thuê tải ảnh CCCD ở Hồ sơ cá nhân trước khi có hợp đồng. Giấy tờ tra theo người thuê
-- (khóa tenant_id + loại), nên bản ghi không cần gắn nhà trọ; property_id chỉ còn ghi nơi tải lên nếu có.
ALTER TABLE tenant_documents ALTER COLUMN property_id DROP NOT NULL;
