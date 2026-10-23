# Release 1: general-admission reservations

## Personas and product flow

1. An organizer creates one event with one general-admission ticket pool.
2. The event has a configured release window and immutable starting inventory.
3. A simulated customer submits a reservation request for one ticket.
4. If inventory remains, the customer receives a hold expiring in five minutes.
5. A mock confirmation step moves the hold to a confirmed ticket; otherwise it expires and returns to availability.
6. A customer may have at most one HELD or CONFIRMED ticket per event.

## Included in Release 1

- Create or seed events, list event and inventory information.
- Reserve one ticket with a mandatory idempotency key.
- Read a reservation by ID.
- Expire stale holds via a background sweeper (and guard confirmation on expiry).
- Simulate confirmation; no external payment provider.
- Database-backed invariants, concurrency tests and baseline measurements.

## Explicitly excluded from Release 1

- Payment gateways, reconciliation with banks, refunds.
- Assigned seating, ticket transfer, secondary markets, multiple ticket classes.
- Customer login, organizer authorization, anti-bot features.
- Fair waiting rooms (later milestone; reservation order alone is not a fairness guarantee).
- Distributed caches, service decomposition, Kafka, cross-region replication.

**Security note:** `X-Demo-Customer-Id` is a synthetic development identity ONLY. Do not expose this system publicly with that header accepted as authentication.

## Definition of done

- Behavior matches the API contract and state machine.
- Concurrent clients cannot oversell or create duplicate active reservations for one customer.
- Each successful state change atomically updates reservation and inventory.
- Expiration versus confirmation produces one winner.
- Retrying after a lost response never creates a second reservation.
- Verification SQL returns no rows after tests.
- Benchmark report includes hardware, workload, error rate, throughput, p50/p95/p99 latency, and a known bottleneck.
