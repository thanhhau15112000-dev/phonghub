-- Xóa giấy tờ cần owner/admin xác nhận (#71 bổ sung): ghi nhận đã báo owner khi tenant đến hạn xóa, để chỉ báo một lần.
ALTER TABLE tenant_privacy ADD COLUMN purge_notified_at TIMESTAMPTZ;
