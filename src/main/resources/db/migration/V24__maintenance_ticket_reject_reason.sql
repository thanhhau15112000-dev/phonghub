-- Từ chối phiếu bảo trì (#68): lưu lý do để người thuê biết vì sao phiếu bị từ chối.
ALTER TABLE maintenance_tickets ADD COLUMN reject_reason TEXT;

ALTER TABLE maintenance_tickets
    ADD CONSTRAINT ck_maintenance_tickets_reject_reason CHECK (
        status <> 'REJECTED' OR (reject_reason IS NOT NULL AND length(btrim(reject_reason)) > 0)) NOT VALID;
