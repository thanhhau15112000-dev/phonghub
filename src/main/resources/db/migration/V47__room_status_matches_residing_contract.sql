-- Phòng còn người ở theo hợp đồng (ACTIVE, hoặc EXPIRED chưa trả phòng) phải là OCCUPIED. Trước bản sửa này, tạo
-- phiếu bảo trì cho phòng có người ở chuyển phòng sang MAINTENANCE và xử lý xong phiếu thì về AVAILABLE, nên hợp đồng
-- không kết thúc được và tỷ lệ lấp đầy bị tính sai. Phiếu bảo trì của các phòng này giữ nguyên.
UPDATE rooms r
SET status = 'OCCUPIED',
    version = r.version + 1,
    updated_at = NOW()
WHERE r.status <> 'OCCUPIED'
  AND r.archived_at IS NULL
  AND EXISTS (
      SELECT 1 FROM contracts c
      WHERE c.room_id = r.id
        AND c.status IN ('ACTIVE', 'EXPIRED')
  );
