-- Quyền xem giấy tờ danh tính tenant tách riêng cho staff (#70 bổ sung).
-- Staff hiện có vẫn xem được như trước (giữ nguyên hành vi); staff được phân công mới mặc định không có quyền này.
ALTER TABLE staff_property_assignments ADD COLUMN can_view_documents BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE staff_property_assignments spa
SET can_view_documents = TRUE
FROM users u
WHERE u.id = spa.staff_user_id AND u.role = 'STAFF';
