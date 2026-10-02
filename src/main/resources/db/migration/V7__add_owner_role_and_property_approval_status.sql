-- =============================================================================
-- PHONGHUB - MIGRATION V7: ADD OWNER ROLE AND PROPERTY APPROVAL STATUS
-- =============================================================================

-- 1. Cập nhật ràng buộc vai trò của người dùng bao gồm OWNER
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'STAFF', 'TECHNICIAN', 'TENANT', 'OWNER'));

-- 2. Bổ sung các cột owner_id, approval_status, rejection_reason vào properties
ALTER TABLE properties ADD COLUMN IF NOT EXISTS owner_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) NOT NULL DEFAULT 'APPROVED' CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'));
ALTER TABLE properties ADD COLUMN IF NOT EXISTS rejection_reason TEXT;

-- 3. Tạo index tối ưu hóa truy vấn theo chủ sở hữu và trạng thái duyệt
CREATE INDEX IF NOT EXISTS idx_properties_owner_id ON properties(owner_id);
CREATE INDEX IF NOT EXISTS idx_properties_approval_status ON properties(approval_status);
