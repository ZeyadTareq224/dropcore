# Correctness specification: v1

## Definitions

For each event:
- T: immutable initialized ticket supply.
- A: currently available tickets.
- H: reserved but unconfirmed tickets (reservation row status HELD, **including holds whose deadline passed but have not yet been transitioned**).
- S: confirmed tickets (reservation row status CONFIRMED).

Invariants:

1. `T = A + H + S` after each committed inventory-changing transaction.
2. `A >= 0`, `H >= 0`, `S >= 0`.
3. For an event+customer, at most one reservation has status `HELD` or `CONFIRMED`.
4. A reservation has a single valid state transition: `HELD -> CONFIRMED | EXPIRED | CANCELLED`; terminal states cannot transition further.
5. A `HELD -> EXPIRED/CANCELLED` transition changes `(A, H, S)` by `(+1, -1, 0)` exactly once.
6. A `HELD -> CONFIRMED` transition changes `(A, H, S)` by `(0, -1, +1)` exactly once.
7. `AVAILABLE -> HELD` changes counters by `(-1, +1, 0)` and creates exactly one HELD reservation in the *same transaction*.
8. A successful retry with the same idempotency key and same input replays the original result, not a new reservation. Reuse of the key with different inputs is an error.
9. Stored final business failures (`SOLD_OUT`, `ACTIVE_RESERVATION_EXISTS`) replay for the same idempotency key. Transient infrastructure failures are not finalized as business outcomes.
10. A reservation is confirmable only if its status is HELD and the authoritative DB time is strictly before `expires_at` when the confirmation wins the row/state transition. A late external payment success (future milestone) must be reconciled/refunded rather than issued as a ticket.

## Important clocks / states

`expires_at` is a **deadline**, not evidence that inventory has already been released. The held inventory remains allocated until the expiration transaction has committed. This eliminates a common double-allocation bug.

## Linearization and transactional requirements

- A reservation success becomes durable at database commit, not when HTTP returns.
- Concurrent conditional inventory updates serialize on the affected PostgreSQL row in the baseline. Each request MUST check affected-row count and must not infer success from a prior `SELECT available`.
- The DB check constraint enforces only counter arithmetic; the application transaction and independent reconciliation query are also required to prove that reservation rows match counts.
- The baseline is single-primary-region PostgreSQL, not multi-region linearizability. We have not proved exactly-once network delivery.
- The idempotency record is persisted with the outcome inside the same transaction as the reservation, and concurrent identical keys are coordinated through a unique database constraint.

## Fault scenarios to test

- 1000 customers compete for 10 tickets.
- 100 requests repeat an identical key for one customer.
- One customer uses 100 different keys at once.
- Sweeper races a confirmation.
- App crashes before commit (rollback), after commit but before HTTP response (replay), and after response.
- The DB connection is interrupted; outcomes may be ambiguous to callers, which must retry with same key.
- Multiple app instances operate against one database.

## What is NOT guaranteed yet

No fairness or FIFO guarantee; no payment reconciliation; no proven million-user capacity; no availability SLO; no cross-region disaster-recovery guarantee.
