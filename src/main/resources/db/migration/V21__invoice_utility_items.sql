-- Dòng điện, nước, dịch vụ trên hóa đơn tháng (#66).
-- reading_id/rate_id cho biết dòng được tính từ chỉ số và đơn giá nào, để scheduler không thêm trùng.

ALTER TABLE invoice_items
    ADD COLUMN reading_id UUID REFERENCES meter_readings(id) ON DELETE SET NULL,
    ADD COLUMN rate_id UUID REFERENCES service_rates(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uk_invoice_items_reading_rate
    ON invoice_items (invoice_id, reading_id, rate_id)
    WHERE reading_id IS NOT NULL AND rate_id IS NOT NULL;
