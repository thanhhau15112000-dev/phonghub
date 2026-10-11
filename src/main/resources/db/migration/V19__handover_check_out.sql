-- Biên bản check-out (#63): dùng chung bảng handovers với check-in (type = 'CHECK_OUT').
-- Mỗi tài sản có thể bị đề xuất khấu trừ; owner chốt tổng khấu trừ khi duyệt.

ALTER TABLE handovers
    ADD COLUMN final_deduction NUMERIC(12, 2)
        CONSTRAINT ck_handovers_final_deduction CHECK (final_deduction IS NULL OR final_deduction >= 0);

ALTER TABLE handover_items
    ADD COLUMN deduction_amount NUMERIC(12, 2) NOT NULL DEFAULT 0
        CONSTRAINT ck_handover_items_deduction CHECK (deduction_amount >= 0);
