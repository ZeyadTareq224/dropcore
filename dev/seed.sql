-- Development fixture, run after Flyway V1. This script is idempotent.
INSERT INTO events (id, title, status, sales_start_at, sales_end_at)
VALUES ('00000000-0000-4000-8000-000000000001', 'Midnight Concert (Demo)', 'OPEN', now() - INTERVAL '1 hour', now() + INTERVAL '30 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO event_inventory (event_id, total, available, held, sold)
VALUES ('00000000-0000-4000-8000-000000000001', 100, 100, 0, 0)
ON CONFLICT (event_id) DO NOTHING;
