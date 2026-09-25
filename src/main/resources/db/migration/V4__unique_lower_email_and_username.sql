-- =============================================================================
-- PHONGHUB - MIGRATION V4: CASE-INSENSITIVE UNIQUE EMAIL AND USERNAME
-- =============================================================================

-- 1. Kiểm tra dữ liệu cũ bị trùng lặp trước khi áp dụng (Pre-check duplicates)
DO $$
DECLARE
    dup_email_count INT;
    dup_username_count INT;
BEGIN
    SELECT COUNT(*) INTO dup_email_count
    FROM (
        SELECT LOWER(TRIM(email))
        FROM users
        GROUP BY LOWER(TRIM(email))
        HAVING COUNT(*) > 1
    ) sub;

    IF dup_email_count > 0 THEN
        RAISE EXCEPTION 'Không thể áp dụng migration V4: Phát hiện % nhóm email bị trùng lặp khi chuyển về chữ thường. Vui lòng xử lý dữ liệu trước.', dup_email_count;
    END IF;

    SELECT COUNT(*) INTO dup_username_count
    FROM (
        SELECT LOWER(TRIM(username))
        FROM users
        WHERE username IS NOT NULL
        GROUP BY LOWER(TRIM(username))
        HAVING COUNT(*) > 1
    ) sub;

    IF dup_username_count > 0 THEN
        RAISE EXCEPTION 'Không thể áp dụng migration V4: Phát hiện % nhóm username bị trùng lặp khi chuyển về chữ thường. Vui lòng xử lý dữ liệu trước.', dup_username_count;
    END IF;
END $$;

-- 2. Chuẩn hoá dữ liệu hiện tại về chữ thường và cắt khoảng trắng
UPDATE users SET email = LOWER(TRIM(email)), username = LOWER(TRIM(username));

-- 3. Đảm bảo tính duy nhất không phân biệt hoa thường ở mức CSDL
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_lower_email ON users (LOWER(email));
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_lower_username ON users (LOWER(username));
