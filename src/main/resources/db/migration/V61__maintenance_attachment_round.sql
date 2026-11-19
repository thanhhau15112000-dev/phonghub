-- Đợt sửa của ảnh kết quả (#232): mỗi lần người thuê báo "Chưa đạt" và sửa lại là một đợt mới.
ALTER TABLE maintenance_attachments
    ADD COLUMN round INT NOT NULL DEFAULT 1 CONSTRAINT ck_maintenance_attachments_round CHECK (round >= 1);

-- Tối đa một video cho mỗi phiếu, mỗi loại, mỗi đợt.
DROP INDEX uk_maintenance_attachments_one_video;
CREATE UNIQUE INDEX uk_maintenance_attachments_one_video ON maintenance_attachments (ticket_id, phase, round) WHERE kind = 'VIDEO';
