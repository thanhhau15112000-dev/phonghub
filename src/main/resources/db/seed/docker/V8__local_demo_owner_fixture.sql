-- =============================================================================
-- PHONGHUB - DOCKER FIXTURE V8: LOCAL DEMO OWNER DATA
-- =============================================================================

-- 1. Thêm 2 tài khoản chủ trọ demo (owner1 và owner2)
INSERT INTO users (id, email, username, full_name, phone, role, status, must_change_password)
VALUES
    ('55555555-0000-0000-0000-000000000001', 'owner1@phonghub.local', 'owner1', 'Chủ trọ Nguyễn Văn B', '0900000005', 'OWNER', 'ACTIVE', FALSE),
    ('55555555-0000-0000-0000-000000000002', 'owner2@phonghub.local', 'owner2', 'Chủ trọ Trần Thị C', '0900000006', 'OWNER', 'ACTIVE', FALSE)
ON CONFLICT (id) DO UPDATE SET
    role = EXCLUDED.role,
    full_name = EXCLUDED.full_name;

-- 2. Gán các nhà trọ hiện có cho owner1 với các trạng thái duyệt khác nhau
UPDATE properties
SET owner_id = '55555555-0000-0000-0000-000000000001',
    approval_status = 'VERIFIED'
WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';

UPDATE properties
SET owner_id = '55555555-0000-0000-0000-000000000001',
    approval_status = 'PENDING'
WHERE id = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';

-- 3. Thêm nhà trọ bị từ chối kèm lý do cho owner1 để kiểm thử giao diện
INSERT INTO properties (id, name, address, description, total_rooms, owner_id, approval_status, rejection_reason)
VALUES
    ('aaaaaaaa-3333-3333-3333-333333333333', 'Nhà trọ Bình Thạnh', '78 Xô Viết Nghệ Tĩnh, Phường 21, Bình Thạnh, TP.HCM', 'Nhà trọ ven sông', 8, '55555555-0000-0000-0000-000000000001', 'REJECTED', 'Giấy phép kinh doanh chưa hợp lệ hoặc thiếu chứng nhận PCCC')
ON CONFLICT (id) DO UPDATE SET
    owner_id = EXCLUDED.owner_id,
    approval_status = EXCLUDED.approval_status,
    rejection_reason = EXCLUDED.rejection_reason;
