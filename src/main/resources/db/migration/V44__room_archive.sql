-- #89: ngừng sử dụng (xóa mềm) phòng. Phòng đã ngừng không hiện trong danh sách, không tạo hợp đồng mới;
-- số phòng vẫn giữ (ràng buộc unique theo nhà trọ) và hợp đồng, hóa đơn, phiếu bảo trì cũ vẫn tham chiếu tới.
ALTER TABLE rooms ADD COLUMN archived_at TIMESTAMPTZ;
