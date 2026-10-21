-- #89: ẩn (xóa mềm) nhà trọ. Nhà trọ đã ẩn không hiện trong danh sách, không tạo thêm phòng/hợp đồng;
-- hợp đồng, hóa đơn cũ vẫn tham chiếu tới nên không xóa cứng.
ALTER TABLE properties ADD COLUMN archived_at TIMESTAMPTZ;
