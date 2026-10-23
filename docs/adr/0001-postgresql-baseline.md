# ADR-0001: PostgreSQL as the baseline allocation authority

**Status:** Accepted for Release 1, explicitly subject to load tests.

## Context

General-admission ticket allocation requires strong per-event inventory accounting and consistent customer reservations during concurrency. We need an understandable reference implementation whose correctness can be verified.

## Decision

Use a single Spring Boot application backed by one PostgreSQL primary. Within a DB transaction, use an atomic conditional `UPDATE event_inventory ... WHERE available > 0`, then create reservation + durable idempotency outcome, relying on the DB as the concurrency coordinator. Expiration/confirmation also atomically mutate reservation and counters.

Use `READ COMMITTED` initially, with condition checks and constraints, then validate assumptions via tests. Use Spring JDBC to make critical SQL explicit.

## Why this baseline

- Provides a well-defined atomic boundary.
- Easy to test independently from app-instance count.
- Fewer failure modes than a cache + queue + database across multiple atomicity domains.
- Avoids adding architecture before measuring demand.

## Known risks and hypotheses

- **Hot row:** every reservation for one event contends on one inventory row. Might cap throughput even while CPU is available.
- **DB saturation:** waiting clients consume pool capacity; admission control might be needed.
- **Fairness:** transaction acquisition order is not user arrival order.
- **No cross-region writes:** one PostgreSQL primary is not a global consensus protocol.

## Alternatives to compare later

Row-level locks with `SELECT ... FOR UPDATE`, allocation token pools, partitions/shards, admission queues, and actor-like single-writer allocation. **Do not assume these outperform the baseline**; benchmark them under identical constraints.

## Evidence required to supersede this decision

Document workload, machine spec, correctness checks, throughput, tail latency, failure modes, and the cost in complexity/operations. Optimize the measured bottleneck, not a hypothetical one.
