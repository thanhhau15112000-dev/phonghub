-- =============================================================================
-- PhongHub - Seed 1 hợp đồng ACTIVE có kỳ thanh toán ĐẾN HẠN cho tài khoản người thuê
-- =============================================================================
-- Điều kiện: đã chạy Flyway tới V11 (app khởi động với profile prod sẽ tự migrate).
-- Cách chạy: Supabase Dashboard > SQL Editor, dán toàn bộ file, Run.
--
-- Hành vi (idempotent, chạy lại không tạo trùng):
--   1. Chọn tài khoản TENANT: theo v_username nếu khai báo, ngược lại lấy tài khoản
--      TENANT ACTIVE mới nhất có hồ sơ tenants.
--   2. Dùng hợp đồng ACTIVE hiện có của người thuê (vai trò người thuê chính);
--      nếu chưa có, tạo hợp đồng ACTIVE trên một phòng AVAILABLE của nhà trọ VERIFIED
--      và chuyển phòng sang OCCUPIED.
--   3. Tạo kỳ thanh toán tháng hiện tại, trạng thái ISSUED, hạn = CURRENT_DATE,
--      kèm mã nội dung chuyển khoản "PH" + 8 ký tự. Nếu kỳ tháng này đã tồn tại thì giữ nguyên.
--   4. Hiển thị kết quả (mã chuyển khoản, số tiền, hạn) ở cuối.
-- =============================================================================

CREATE TEMP TABLE IF NOT EXISTS _phonghub_seed_result (
    username TEXT,
    contract_id UUID,
    invoice_id UUID,
    period TEXT,
    amount NUMERIC(12, 2),
    due_date DATE,
    status TEXT,
    payment_code TEXT,
    note TEXT
) ON COMMIT PRESERVE ROWS;
TRUNCATE _phonghub_seed_result;

DO $$
DECLARE
    -- Đổi thành username cụ thể nếu cần, ví dụ: 'tenant_username'
    v_username   TEXT := NULL;

    v_user_id    UUID;
    v_tenant_id  UUID;
    v_contract   contracts%ROWTYPE;
    v_room       rooms%ROWTYPE;
    v_invoice    invoices%ROWTYPE;
    v_start      DATE;
    v_rent       NUMERIC(12, 2);
    v_code       TEXT;
    v_note       TEXT := '';
BEGIN
    -- 0. Kiểm tra migration V11
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'invoices' AND column_name = 'payment_code'
    ) THEN
        RAISE EXCEPTION 'Thiếu cột invoices.payment_code: cần chạy migration V11 (deploy bản có V11) trước.';
    END IF;

    -- 1. Tài khoản người thuê và hồ sơ tenants
    SELECT u.id, u.username, t.id
      INTO v_user_id, v_username, v_tenant_id
      FROM users u
      JOIN tenants t ON t.user_id = u.id
     WHERE u.role = 'TENANT'
       AND u.status = 'ACTIVE'
       AND (v_username IS NULL OR lower(u.username) = lower(v_username))
     ORDER BY u.created_at DESC
     LIMIT 1;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Không tìm thấy tài khoản TENANT ACTIVE có hồ sơ người thuê (username = %).',
            coalesce(v_username, '<tự chọn>');
    END IF;

    -- 2. Hợp đồng ACTIVE của người thuê chính
    SELECT * INTO v_contract
      FROM contracts
     WHERE primary_tenant_id = v_tenant_id AND status = 'ACTIVE'
     ORDER BY created_at DESC
     LIMIT 1;

    IF v_contract.id IS NULL THEN
        SELECT r.* INTO v_room
          FROM rooms r
          JOIN properties p ON p.id = r.property_id
         WHERE r.status = 'AVAILABLE'
           -- approval_status có từ V7; DB chưa chạy V7 thì coi như đã duyệt
           AND coalesce(to_jsonb(p) ->> 'approval_status', 'VERIFIED') IN ('VERIFIED', 'APPROVED')
           AND NOT EXISTS (SELECT 1 FROM contracts c WHERE c.room_id = r.id AND c.status = 'ACTIVE')
         ORDER BY p.created_at, r.room_number
         LIMIT 1
         FOR UPDATE OF r;

        IF v_room.id IS NULL THEN
            RAISE EXCEPTION 'Người thuê chưa có hợp đồng ACTIVE và không còn phòng AVAILABLE thuộc nhà trọ VERIFIED để tạo hợp đồng.';
        END IF;

        v_start := (date_trunc('month', CURRENT_DATE) - INTERVAL '1 month')::date;
        v_rent  := CASE WHEN v_room.base_price > 0 THEN v_room.base_price ELSE 3000000 END;

        INSERT INTO contracts (
            property_id, room_id, primary_tenant_id, deposit_amount, rent_amount,
            start_date, end_date, payment_day, status
        ) VALUES (
            v_room.property_id, v_room.id, v_tenant_id, v_rent, v_rent,
            v_start, (v_start + INTERVAL '12 months')::date,
            EXTRACT(DAY FROM CURRENT_DATE)::int, 'ACTIVE'
        )
        RETURNING * INTO v_contract;

        INSERT INTO contract_occupants (contract_id, tenant_id, is_primary, check_in_date)
        VALUES (v_contract.id, v_tenant_id, TRUE, v_start);

        UPDATE rooms SET status = 'OCCUPIED', updated_at = CURRENT_TIMESTAMP WHERE id = v_room.id;

        v_note := 'Tạo hợp đồng mới cho phòng ' || v_room.room_number || '. ';
    ELSE
        v_note := 'Dùng hợp đồng ACTIVE hiện có. ';
    END IF;

    -- 3. Kỳ thanh toán tháng hiện tại
    SELECT * INTO v_invoice
      FROM invoices
     WHERE contract_id = v_contract.id
       AND year = EXTRACT(YEAR FROM CURRENT_DATE)::int
       AND month = EXTRACT(MONTH FROM CURRENT_DATE)::int
       AND status <> 'VOIDED'
     LIMIT 1;

    IF v_invoice.id IS NULL THEN
        LOOP
            v_code := 'PH' || upper(substr(md5(gen_random_uuid()::text), 1, 8));
            EXIT WHEN NOT EXISTS (SELECT 1 FROM invoices WHERE payment_code = v_code);
        END LOOP;

        INSERT INTO invoices (
            contract_id, room_id, tenant_id, month, year,
            rent_amount, utility_amount, other_amount, total_amount, paid_amount,
            due_date, status, payment_code
        ) VALUES (
            v_contract.id, v_contract.room_id, v_tenant_id,
            EXTRACT(MONTH FROM CURRENT_DATE)::int, EXTRACT(YEAR FROM CURRENT_DATE)::int,
            v_contract.rent_amount, 0, 0, v_contract.rent_amount, 0,
            CURRENT_DATE, 'ISSUED', v_code
        )
        RETURNING * INTO v_invoice;

        v_note := v_note || 'Tạo kỳ thanh toán mới.';
    ELSE
        v_note := v_note || 'Kỳ tháng này đã tồn tại, giữ nguyên (trạng thái ' || v_invoice.status || ').';
    END IF;

    INSERT INTO _phonghub_seed_result VALUES (
        v_username, v_contract.id, v_invoice.id,
        lpad(v_invoice.month::text, 2, '0') || '/' || v_invoice.year,
        v_invoice.total_amount - v_invoice.paid_amount,
        v_invoice.due_date, v_invoice.status, v_invoice.payment_code, v_note
    );
END $$;

SELECT * FROM _phonghub_seed_result;
