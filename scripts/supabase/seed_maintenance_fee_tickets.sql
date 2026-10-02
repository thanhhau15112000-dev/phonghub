-- =============================================================================
-- PhongHub - Seed 4 phiếu bảo trì "Chờ thanh toán" (phí sửa chữa người thuê chịu)
-- =============================================================================
-- Điều kiện: đã áp V12 (apply_v12_maintenance_fee_schema.sql) và tài khoản người thuê
--            đã có hợp đồng ACTIVE (chạy seed_due_rent_invoice.sql nếu chưa có).
-- Cách chạy: Supabase Dashboard > SQL Editor, dán toàn bộ file, Run.
--
-- Hành vi (idempotent, chạy lại không tạo trùng):
--   1. Chọn tài khoản TENANT: theo v_username nếu khai báo, ngược lại lấy tài khoản
--      TENANT ACTIVE mới nhất có hồ sơ tenants và hợp đồng ACTIVE (người thuê chính).
--   2. Với mỗi trong 4 sự cố mẫu, tạo phiếu bảo trì trạng thái AWAITING_PAYMENT
--      (nguyên nhân TENANT_USAGE, người thuê chịu phí) và một hóa đơn phí sửa chữa
--      ISSUED, hạn 7 ngày, kèm mã chuyển khoản "PH" + 8 ký tự.
--   3. Phiếu cùng tiêu đề trong cùng phòng đã có thì bỏ qua; thiếu hóa đơn thì bổ sung.
--   4. Hiển thị kết quả (phiếu, số tiền, hạn, mã chuyển khoản) ở cuối.
-- =============================================================================

SET client_encoding = 'UTF8';

CREATE TEMP TABLE IF NOT EXISTS _phonghub_fee_seed_result (
    username TEXT,
    room TEXT,
    ticket_title TEXT,
    ticket_status TEXT,
    amount NUMERIC(12, 2),
    due_date DATE,
    invoice_status TEXT,
    payment_code TEXT,
    note TEXT
) ON COMMIT PRESERVE ROWS;
TRUNCATE _phonghub_fee_seed_result;

DO $$
DECLARE
    -- Đổi thành username cụ thể nếu cần, ví dụ: 'tenant_username'
    v_username   TEXT := NULL;

    v_user_id    UUID;
    v_tenant_id  UUID;
    v_contract   contracts%ROWTYPE;
    v_room_no    TEXT;
    v_tech_id    UUID;
    v_ticket_id  UUID;
    v_invoice    invoices%ROWTYPE;
    v_code       TEXT;
    v_note       TEXT;
    v_fixture    RECORD;
BEGIN
    -- 0. Kiểm tra migration V12
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'invoices' AND column_name = 'ticket_id'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'maintenance_tickets' AND column_name = 'liable_party'
    ) THEN
        RAISE EXCEPTION 'Thiếu cột của V12: chạy apply_v12_maintenance_fee_schema.sql trước.';
    END IF;

    -- 1. Người thuê có hợp đồng ACTIVE
    SELECT u.id, u.username, t.id
      INTO v_user_id, v_username, v_tenant_id
      FROM users u
      JOIN tenants t ON t.user_id = u.id
     WHERE u.role = 'TENANT'
       AND u.status = 'ACTIVE'
       AND (v_username IS NULL OR lower(u.username) = lower(v_username))
       AND EXISTS (SELECT 1 FROM contracts c WHERE c.primary_tenant_id = t.id AND c.status = 'ACTIVE')
     ORDER BY u.created_at DESC
     LIMIT 1;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Không tìm thấy tài khoản TENANT ACTIVE có hợp đồng ACTIVE (username = %). Chạy seed_due_rent_invoice.sql trước.',
            coalesce(v_username, '<tự chọn>');
    END IF;

    SELECT * INTO v_contract
      FROM contracts
     WHERE primary_tenant_id = v_tenant_id AND status = 'ACTIVE'
     ORDER BY created_at DESC
     LIMIT 1;

    SELECT room_number INTO v_room_no FROM rooms WHERE id = v_contract.room_id;

    -- Kỹ thuật viên phụ trách (có thể rỗng)
    SELECT id INTO v_tech_id
      FROM users WHERE role = 'TECHNICIAN' AND status = 'ACTIVE'
     ORDER BY created_at LIMIT 1;

    -- 2. 4 sự cố do người thuê sử dụng
    FOR v_fixture IN
        SELECT * FROM (VALUES
            (1, 'Vỡ kính cửa sổ phòng',                        'Kính cửa sổ bị vỡ do va đập, đã thay kính mới.',                         450000::numeric),
            (2, 'Hỏng ổ khóa cửa do làm mất chìa',             'Mất chìa khóa nên phải phá và thay ổ khóa cửa chính.',                   300000::numeric),
            (3, 'Hỏng vòi sen nhà tắm do va đập',              'Vòi sen bị gãy do va đập, đã thay bộ vòi sen mới.',                      180000::numeric),
            (4, 'Cháy ổ cắm do dùng thiết bị công suất lớn',   'Ổ cắm bị cháy khi dùng nhiều thiết bị công suất lớn, đã thay ổ cắm mới.', 250000::numeric)
        ) AS f(seq, title, notes, cost)
        ORDER BY seq
    LOOP
        v_note := '';

        SELECT id INTO v_ticket_id
          FROM maintenance_tickets
         WHERE room_id = v_contract.room_id AND title = v_fixture.title
         LIMIT 1;

        IF v_ticket_id IS NULL THEN
            INSERT INTO maintenance_tickets (
                room_id, requested_by_tenant_id, assigned_technician_id, title, description,
                priority, status, repair_cost, resolution_notes, cause_category, liable_party
            ) VALUES (
                v_contract.room_id, v_tenant_id, v_tech_id, v_fixture.title,
                'Sự cố do sử dụng: ' || v_fixture.notes,
                'MEDIUM', 'AWAITING_PAYMENT', v_fixture.cost, v_fixture.notes, 'TENANT_USAGE', 'TENANT'
            )
            RETURNING id INTO v_ticket_id;
            v_note := 'Tạo phiếu mới. ';
        ELSE
            v_note := 'Phiếu đã tồn tại. ';
        END IF;

        SELECT * INTO v_invoice
          FROM invoices
         WHERE ticket_id = v_ticket_id AND status <> 'VOIDED'
         LIMIT 1;

        IF v_invoice.id IS NULL THEN
            LOOP
                v_code := 'PH' || upper(substr(md5(gen_random_uuid()::text), 1, 8));
                EXIT WHEN NOT EXISTS (SELECT 1 FROM invoices WHERE payment_code = v_code);
            END LOOP;

            INSERT INTO invoices (
                contract_id, room_id, tenant_id, month, year,
                rent_amount, utility_amount, other_amount, total_amount, paid_amount,
                due_date, status, payment_code, invoice_type, ticket_id
            ) VALUES (
                v_contract.id, v_contract.room_id, v_tenant_id,
                EXTRACT(MONTH FROM CURRENT_DATE)::int, EXTRACT(YEAR FROM CURRENT_DATE)::int,
                0, 0, v_fixture.cost, v_fixture.cost, 0,
                CURRENT_DATE + 7, 'ISSUED', v_code, 'MAINTENANCE', v_ticket_id
            )
            RETURNING * INTO v_invoice;
            v_note := v_note || 'Tạo hóa đơn phí sửa chữa.';
        ELSE
            v_note := v_note || 'Hóa đơn đã có, giữ nguyên.';
        END IF;

        INSERT INTO _phonghub_fee_seed_result
        SELECT v_username, v_room_no, t.title, t.status,
               v_invoice.total_amount - v_invoice.paid_amount, v_invoice.due_date,
               v_invoice.status, v_invoice.payment_code, v_note
          FROM maintenance_tickets t WHERE t.id = v_ticket_id;

        v_ticket_id := NULL;
        v_invoice := NULL;
    END LOOP;
END $$;

SELECT * FROM _phonghub_fee_seed_result;
