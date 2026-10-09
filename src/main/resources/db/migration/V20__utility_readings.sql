-- Chỉ số điện nước theo kỳ (#65).
-- meter_readings thêm khái niệm kỳ (tháng), hỗ trợ thay đồng hồ và cột kiểm toán; mỗi phòng/loại/kỳ chỉ một bản ghi.

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    FOR constraint_name IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'meter_readings'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%current_value >= previous_value%'
    LOOP
        EXECUTE format('ALTER TABLE meter_readings DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

ALTER TABLE meter_readings DROP COLUMN consumption;

ALTER TABLE meter_readings
    ADD COLUMN period_year INT,
    ADD COLUMN period_month INT,
    ADD COLUMN consumption NUMERIC(10, 2),
    ADD COLUMN replaced BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN old_meter_final_value NUMERIC(10, 2),
    ADD COLUMN new_meter_start_value NUMERIC(10, 2),
    ADD COLUMN replacement_note TEXT,
    ADD COLUMN created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_by UUID REFERENCES users(id) ON DELETE SET NULL;

UPDATE meter_readings
SET period_year = EXTRACT(YEAR FROM reading_date)::INT,
    period_month = EXTRACT(MONTH FROM reading_date)::INT,
    consumption = GREATEST(current_value - previous_value, 0);

ALTER TABLE meter_readings
    ALTER COLUMN period_year SET NOT NULL,
    ALTER COLUMN period_month SET NOT NULL,
    ALTER COLUMN consumption SET NOT NULL,
    ADD CONSTRAINT ck_meter_readings_period CHECK (period_month BETWEEN 1 AND 12 AND period_year >= 2020),
    ADD CONSTRAINT ck_meter_readings_consumption CHECK (consumption >= 0),
    ADD CONSTRAINT ck_meter_readings_not_decreasing CHECK (replaced OR current_value >= previous_value),
    ADD CONSTRAINT ck_meter_readings_replacement CHECK (
        NOT replaced OR (old_meter_final_value IS NOT NULL AND new_meter_start_value IS NOT NULL
                         AND replacement_note IS NOT NULL AND length(btrim(replacement_note)) > 0));

CREATE UNIQUE INDEX uk_meter_readings_period ON meter_readings (room_id, meter_type, period_year, period_month);
CREATE INDEX idx_meter_readings_room_period ON meter_readings (room_id, meter_type, period_year DESC, period_month DESC);

ALTER TABLE service_rates
    ADD COLUMN created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_by UUID REFERENCES users(id) ON DELETE SET NULL;
