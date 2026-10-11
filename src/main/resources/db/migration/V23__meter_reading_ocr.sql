-- OCR đọc chỉ số đồng hồ (#67): lưu giá trị OCR gợi ý cùng độ tin cậy bên cạnh giá trị đã xác nhận (current_value).
ALTER TABLE meter_readings
    ADD COLUMN ocr_value NUMERIC(10, 2),
    ADD COLUMN ocr_confidence NUMERIC(4, 3),
    ADD CONSTRAINT ck_meter_readings_ocr CHECK (
        (ocr_value IS NULL AND ocr_confidence IS NULL)
        OR (ocr_value >= 0 AND ocr_confidence BETWEEN 0 AND 1));
