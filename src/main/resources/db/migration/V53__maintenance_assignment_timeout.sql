-- #223: track the latest technician assignment and the one-time understaffed alert.
ALTER TABLE maintenance_tickets
    ADD COLUMN assigned_at TIMESTAMPTZ,
    ADD COLUMN understaffed_notified_at TIMESTAMPTZ;

-- Existing rows have no exact assignment timestamp; start their 72-hour clock at deployment
-- to avoid an unbounded first-run alert for old tickets.
UPDATE maintenance_tickets
SET assigned_at = CURRENT_TIMESTAMP
WHERE assigned_technician_id IS NOT NULL
  AND status IN ('ASSIGNED', 'IN_PROGRESS');

CREATE INDEX idx_maintenance_tickets_assignment_timeout
    ON maintenance_tickets (assigned_at)
    WHERE assigned_at IS NOT NULL
      AND status IN ('ASSIGNED', 'IN_PROGRESS');
