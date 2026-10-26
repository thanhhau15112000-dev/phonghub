-- #225: the billing contract allows late fees only after at least seven overdue days.
UPDATE property_late_fee_policies
SET grace_days = 7
WHERE grace_days < 7;

ALTER TABLE property_late_fee_policies
    ALTER COLUMN grace_days SET DEFAULT 7;

ALTER TABLE property_late_fee_policies
    DROP CONSTRAINT IF EXISTS property_late_fee_policies_grace_days_check,
    ADD CONSTRAINT property_late_fee_policies_grace_days_check
        CHECK (grace_days BETWEEN 7 AND 365);
