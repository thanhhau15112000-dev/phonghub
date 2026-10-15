-- Trung tâm thông báo (#86): lưu thông báo vào DB (trước đây chỉ nằm trong bộ nhớ, mất khi khởi động lại).
-- Tiêu đề, nội dung và ghi chú xử lý được mã hóa ở tầng ứng dụng như các cột thông tin cá nhân khác (V17); tên đăng
-- nhập/họ tên người nhận không lưu lại mà đọc (đã giải mã) từ bảng users.
-- target_user_id: người nhận; riêng PASSWORD_RESET_REQUEST là người yêu cầu (thông báo dành cho quản trị viên).
-- NULL = thông báo hệ thống cho quản trị viên (ví dụ tiền về không khớp hóa đơn).
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    type VARCHAR(40) NOT NULL CHECK (type IN ('PASSWORD_RESET_REQUEST', 'GENERAL', 'PAYMENT_RECEIVED')),
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    target_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    target_role VARCHAR(30),
    link VARCHAR(500) CHECK (link IS NULL OR (link LIKE '/%' AND link NOT LIKE '//%')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMPTZ,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMPTZ,
    resolution_note TEXT
);

-- Hộp thư cá nhân: danh sách mới nhất trước và đếm chưa đọc.
CREATE INDEX idx_notifications_recipient ON notifications (target_user_id, created_at DESC)
    WHERE type <> 'PASSWORD_RESET_REQUEST';
CREATE INDEX idx_notifications_recipient_unread ON notifications (target_user_id)
    WHERE read_at IS NULL AND type <> 'PASSWORD_RESET_REQUEST';
-- Việc của quản trị viên.
CREATE INDEX idx_notifications_admin ON notifications (created_at DESC)
    WHERE type = 'PASSWORD_RESET_REQUEST' OR target_user_id IS NULL;

-- Nhắc hóa đơn sắp đến hạn: mỗi (hóa đơn, hạn thanh toán) chỉ nhắc một lần; khóa chính chặn nhắc trùng khi job chạy
-- lại hoặc chạy song song.
CREATE TABLE invoice_due_reminders (
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    due_date DATE NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (invoice_id, due_date)
);
