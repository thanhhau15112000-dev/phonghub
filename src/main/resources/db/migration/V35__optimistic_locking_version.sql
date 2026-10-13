-- Khóa lạc quan (#76): cập nhật đồng thời cùng một bản ghi thì bản sau nhận 409.
ALTER TABLE rooms ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE contracts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE invoices ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE maintenance_tickets ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
