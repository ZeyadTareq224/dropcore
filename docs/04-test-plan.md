# Phase 1 test matrix and first experiments

## Required correctness tests

| ID | Scenario | Pass condition |
|---|---|---|
| C1 | 1000 distinct customers compete for 10 tickets | Exactly 10 distinct HELD; 990 SOLD_OUT; A=0,H=10,S=0 |
| C2 | 100 concurrent retries of one key | One reservation; all successful responses identify same reservation |
| C3 | One customer submits 100 different keys | At most one HELD/CONFIRMED for customer/event |
| C4 | Last ticket claimed by two distinct customers | Exactly one winner |
| C5 | Confirmation races expiration | Exactly one transition wins; counts match state |
| C6 | Expiration runs multiple times | Release once; no inventory inflation |
| C7 | DB transaction rolls back after inventory decrement | Both inventory and reservation roll back |
| C8 | Response lost after successful DB commit; retry same key | Stable replay, no extra hold |
| C9 | Event not open or outside sale window | No inventory mutation |
| C10 | Compare inventory counters with actual HELD/CONFIRMED rows | Reconciliation query returns 0 anomalies |

## Baseline experiment (after C1-C10)

- Document machine CPU, memory, PostgreSQL version, configured connection pool, app instance count, dataset, test duration, and generator environment.
- Warm up before recording measurements.
- Test steady load and an abrupt flash-sale arrival profile separately.
- Report attempted requests/s, successful holds/s, HTTP errors, p50/p95/p99 end-to-end latency, database wait times, CPU, memory, and saturation point.
- Separate `SOLD_OUT` **business outcomes** from infrastructure failures.
- Check correctness after each experiment from a consistent snapshot with `dev/reconcile.sql`.
- Reproduce each experiment at least three times; keep raw test outputs.

Do **not** call a million issued test HTTP requests a million simultaneous users. Throughput, concurrency, and unique customer count are different dimensions.

## Learning-oriented first task

Implement the reservation endpoint and make C1-C4 pass *without using Redis, Kafka, in-memory locks, or a Java `synchronized` block*. The first implementation should use PostgreSQL as the sole coordination authority.

Suggested baseline atomic SQL (inside one database transaction alongside reservation/idempotency inserts):

```sql
UPDATE event_inventory
SET available = available - 1,
    held = held + 1
WHERE event_id = :event_id
  AND available > 0
RETURNING available, held, sold;
```

The request did not reserve anything if this returns zero rows. This SQL is not a complete solution by itself: handle event-sale windows, idempotency races, one-per-customer uniqueness, and transaction failure as specified.
