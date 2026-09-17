-- Cập nhật dữ liệu demo Docker sang tiếng Việt có dấu.
-- Chỉ áp dụng cho profile docker; không thay đổi dữ liệu Supabase/prod.

UPDATE users
SET full_name = CASE id
    WHEN '11111111-1111-1111-1111-111111111111' THEN 'Quản trị viên'
    WHEN '22222222-2222-2222-2222-222222222221' THEN 'Nhân viên Quận 7'
    WHEN '22222222-2222-2222-2222-222222222222' THEN 'Nhân viên Tân Bình'
    WHEN '33333333-3333-3333-3333-333333333331' THEN 'Kỹ thuật viên'
    WHEN '44444444-4444-4444-4444-444444444441' THEN 'Nguyễn Văn A'
END
WHERE id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222221',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333331',
    '44444444-4444-4444-4444-444444444441'
);

UPDATE properties
SET name = CASE id
        WHEN 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' THEN 'Nhà trọ Xanh - Quận 7'
        WHEN 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb' THEN 'Khu trọ Tân Bình'
    END,
    address = CASE id
        WHEN 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' THEN '123 Nguyễn Thị Thập, Phường Tân Quy, Quận 7, TP.HCM'
        WHEN 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb' THEN '45 Cộng Hòa, Phường 13, Quận Tân Bình, TP.HCM'
    END,
    description = CASE id
        WHEN 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa' THEN 'Khu trọ dành cho sinh viên và người đi làm'
        WHEN 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb' THEN 'Nhà trọ gần sân bay, yên tĩnh'
    END
WHERE id IN (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'
);

UPDATE tenants
SET full_name = 'Nguyễn Văn A',
    permanent_address = 'Bến Tre'
WHERE id = '44444444-0000-0000-0000-444444444441';

UPDATE maintenance_tickets
SET title = 'Sửa vòi nước và kiểm tra máy lạnh',
    description = 'Vòi nước bồn rửa bị rỉ, máy lạnh chạy yếu'
WHERE id = 'dddddddd-dddd-dddd-dddd-dddddddddddd';
