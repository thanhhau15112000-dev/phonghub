-- =============================================================================
-- PHONGHUB - MIGRATION V9: STANDARDIZE PROPERTY VERIFICATION STATUS
-- =============================================================================

-- 1. Xóa ràng buộc check cũ trước khi cập nhật dữ liệu (để cho phép gán 'VERIFIED')
ALTER TABLE properties DROP CONSTRAINT IF EXISTS properties_approval_status_check;

-- 2. Chuẩn hóa dữ liệu cũ có trạng thái 'APPROVED' sang 'VERIFIED'
UPDATE properties SET approval_status = 'VERIFIED' WHERE approval_status = 'APPROVED';

-- 3. Tạo lại ràng buộc check mới hỗ trợ ('PENDING', 'VERIFIED', 'REJECTED')
ALTER TABLE properties ADD CONSTRAINT properties_approval_status_check CHECK (approval_status IN ('PENDING', 'VERIFIED', 'REJECTED'));

-- 4. Đặt giá trị mặc định cho cột approval_status là 'VERIFIED'
ALTER TABLE properties ALTER COLUMN approval_status SET DEFAULT 'VERIFIED';
