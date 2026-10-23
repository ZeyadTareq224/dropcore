-- Invariant checker. Must return ZERO rows after all committed work.
-- This checker compares counters with reservation rows; it must run against
-- a consistent database snapshot to avoid false alarms during concurrent writes.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT
  i.event_id,
  i.total, i.available, i.held, i.sold,
  COUNT(r.id) FILTER (WHERE r.status = 'HELD') AS actual_held,
  COUNT(r.id) FILTER (WHERE r.status = 'CONFIRMED') AS actual_sold
FROM event_inventory i
LEFT JOIN reservations r ON r.event_id = i.event_id
GROUP BY i.event_id, i.total, i.available, i.held, i.sold
HAVING i.held <> COUNT(r.id) FILTER (WHERE r.status = 'HELD')
    OR i.sold <> COUNT(r.id) FILTER (WHERE r.status = 'CONFIRMED')
    OR i.total <> i.available + i.held + i.sold;
COMMIT;
