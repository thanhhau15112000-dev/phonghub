-- Phân quyền chi tiết cho staff (#70): từ nay cờ can_manage_contracts / can_collect_payment có hiệu lực.
-- Trước đây staff được phân công làm được mọi việc của nhà trọ, nên giữ nguyên quyền hiện có cho các
-- phân công staff đã tồn tại; owner có thể thu hẹp sau trên trang phân công nhân sự.
UPDATE staff_property_assignments spa
SET can_collect_payment = TRUE,
    can_manage_contracts = TRUE
FROM users u
WHERE u.id = spa.staff_user_id AND u.role = 'STAFF';
