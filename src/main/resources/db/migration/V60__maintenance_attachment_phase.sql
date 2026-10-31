-- Phân loại tệp phiếu báo hỏng (#232): REPORT là ảnh/video lúc báo hỏng, RESULT là ảnh/video kết quả sửa chữa.
ALTER TABLE maintenance_attachments
    ADD COLUMN phase VARCHAR(10) NOT NULL DEFAULT 'REPORT'
        CONSTRAINT ck_maintenance_attachments_phase CHECK (phase IN ('REPORT', 'RESULT'));

-- Tối đa một video cho mỗi phiếu ở mỗi loại; chặn cả khi hai yêu cầu tải lên cùng lúc.
DROP INDEX uk_maintenance_attachments_one_video;
CREATE UNIQUE INDEX uk_maintenance_attachments_one_video ON maintenance_attachments (ticket_id, phase) WHERE kind = 'VIDEO';
