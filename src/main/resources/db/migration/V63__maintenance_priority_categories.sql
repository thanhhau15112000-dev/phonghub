-- #294: use the four user-facing priority groups. Preserve existing SLA deadlines
-- while moving legacy urgent tickets into the high group.
ALTER TABLE maintenance_tickets
    DROP CONSTRAINT IF EXISTS maintenance_tickets_priority_check;

UPDATE maintenance_tickets
SET priority = 'HIGH'
WHERE priority = 'URGENT';

UPDATE maintenance_tickets
SET priority = 'UNCLASSIFIED'
WHERE priority IS NULL;

ALTER TABLE maintenance_tickets
    ALTER COLUMN priority SET DEFAULT 'UNCLASSIFIED',
    ALTER COLUMN priority SET NOT NULL;

ALTER TABLE maintenance_tickets
    ADD CONSTRAINT maintenance_tickets_priority_check
        CHECK (priority IN ('UNCLASSIFIED', 'LOW', 'MEDIUM', 'HIGH'));
