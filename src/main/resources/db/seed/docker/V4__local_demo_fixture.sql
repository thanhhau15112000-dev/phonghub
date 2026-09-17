-- Dữ liệu fixture local cho PostgreSQL trong Docker.
-- Migration này chỉ được nạp ở profile docker; profile prod chỉ quét db/migration.

INSERT INTO users (id, email, username, full_name, phone, role, status, must_change_password)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'admin@phonghub.local', 'admin', 'Quan Tri Vien', '0900000001', 'ADMIN', 'ACTIVE', FALSE),
    ('22222222-2222-2222-2222-222222222221', 'staff1@phonghub.local', 'staff1', 'Nhan Vien Q7', '0900000002', 'STAFF', 'ACTIVE', FALSE),
    ('22222222-2222-2222-2222-222222222222', 'staff2@phonghub.local', 'staff2', 'Nhan Vien Tan Binh', '0900000003', 'STAFF', 'ACTIVE', FALSE),
    ('33333333-3333-3333-3333-333333333331', 'tech1@phonghub.local', 'tech1', 'Ky Thuat Vien', '0900000004', 'TECHNICIAN', 'ACTIVE', FALSE),
    ('44444444-4444-4444-4444-444444444441', 'tenant1@phonghub.local', 'tenant1', 'Nguyen Van A', '0901234567', 'TENANT', 'ACTIVE', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO properties (id, name, address, description, total_rooms)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Nha Tro Xanh - Quan 7', '123 Nguyen Thi Thap, Phuong Tan Quy, Quan 7, TP.HCM', 'Khu tro sinh vien va nguoi di lam', 10),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Khu Tro Tan Binh', '45 Cong Hoa, Phuong 13, Quan Tan Binh, TP.HCM', 'Nha tro gan san bay, yen tinh', 5)
ON CONFLICT (id) DO NOTHING;

INSERT INTO staff_property_assignments (id, staff_user_id, property_id, can_collect_payment, can_manage_contracts)
VALUES
    ('55555555-5555-5555-5555-555555555551', '22222222-2222-2222-2222-222222222221', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', TRUE, TRUE),
    ('55555555-5555-5555-5555-555555555552', '22222222-2222-2222-2222-222222222222', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', TRUE, TRUE),
    ('55555555-5555-5555-5555-555555555553', '33333333-3333-3333-3333-333333333331', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', FALSE, FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO rooms (id, property_id, room_number, floor, area_sqm, base_price, max_occupants, status)
VALUES
    ('10101010-1010-1010-1010-101010101010', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'P101', 1, 25.00, 3500000, 2, 'OCCUPIED'),
    ('10201020-1020-1020-1020-102010201020', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'P102', 1, 25.00, 3500000, 2, 'MAINTENANCE'),
    ('10301030-1030-1030-1030-103010301030', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'P103', 1, 28.00, 3800000, 2, 'RESERVED'),
    ('10401040-1040-1040-1040-104010401040', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'P104', 1, 30.00, 4000000, 3, 'AVAILABLE'),
    ('20102010-2010-2010-2010-201020102010', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'P201', 2, 20.00, 3000000, 2, 'AVAILABLE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO tenants (id, user_id, full_name, identity_card_number, phone, email, permanent_address)
VALUES
    ('44444444-0000-0000-0000-444444444441', '44444444-4444-4444-4444-444444444441', 'Nguyen Van A', '079201001111', '0901234567', 'tenant1@phonghub.local', 'Ben Tre')
ON CONFLICT (id) DO NOTHING;

INSERT INTO contracts (id, property_id, room_id, primary_tenant_id, deposit_amount, rent_amount, start_date, end_date, payment_day, status)
VALUES
    ('cccccccc-cccc-cccc-cccc-cccccccccccc',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     '10101010-1010-1010-1010-101010101010',
     '44444444-0000-0000-0000-444444444441',
     3500000, 3500000, CURRENT_DATE - INTERVAL '2 months', CURRENT_DATE + INTERVAL '10 months', 5, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO contract_occupants (id, contract_id, tenant_id, is_primary, check_in_date)
VALUES
    ('66666666-6666-6666-6666-666666666661', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '44444444-0000-0000-0000-444444444441', TRUE, CURRENT_DATE - INTERVAL '2 months')
ON CONFLICT (id) DO NOTHING;

INSERT INTO maintenance_tickets (id, room_id, requested_by_tenant_id, assigned_technician_id, title, description, priority, status, repair_cost, resolution_notes)
VALUES
    ('dddddddd-dddd-dddd-dddd-dddddddddddd',
     '10201020-1020-1020-1020-102010201020',
     NULL,
     '33333333-3333-3333-3333-333333333331',
     'Sua voi nuoc va kiem tra may lanh',
     'Voi nuoc bon rua bi ri, may lanh chay yeu',
     'HIGH', 'IN_PROGRESS', 0, NULL)
ON CONFLICT (id) DO NOTHING;
