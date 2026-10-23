# DropCore

**Engineering-first, high-contention ticket allocation platform.**

DropCore begins as a general-admission ticket reservation application. Its long-term flagship value is a reusable allocation engine plus reproducible correctness, load, and fault-injection experiments.

## Objective

Prove that high-concurrency reservations can remain correct under retries, crashes, expiration races, and eventually high-contention workloads. Publish measured performance and failure-mode tradeoffs. **No throughput or million-user claims have been validated yet.**

## Phase 1 stack

- Java 21, Spring Boot 4.1.1, Maven
- Spring MVC, Spring JDBC (`JdbcClient`/`JdbcTemplate`), PostgreSQL 17, Flyway
- One deployable Spring Boot app, one relational database
- No microservices, Redis, queues, or real payments in Phase 1

## Run the scaffold

Prerequisites: Java 21+, Maven, Docker Compose.

```bash
docker compose up -d
mvn spring-boot:run
```

The DB migration runs at startup. The application exposes Actuator health checks
and the Phase 1 create-hold endpoint.

```bash
curl http://localhost:8080/actuator/health
```

Load the demo event:

```bash
docker compose exec -T db psql -U dropcore -d dropcore < dev/seed.sql
```

The database is exposed on localhost port **5433**, configurable in `application.yml` with `DB_PORT`.

## Create a hold

`POST /v1/events/{eventId}/reservations` uses a **single PostgreSQL
transaction**, a conditional inventory decrement, a reservation row, and a
persisted idempotency outcome. PostgreSQL is the sole coordination authority;
the flow does not use a cache, queue, or process-local lock.

The starter SQL is in `src/main/resources/db/migration/V1__initial_schema.sql`. It intentionally exposes the baseline model to make future performance bottlenecks measurable. Track architectural decisions in `docs/adr/`.

## Documentation

- [Release 1 scope](docs/01-scope.md)
- [Invariants and failure semantics](docs/02-invariants.md)
- [API contract](docs/03-api-contract.md)
- [Test plan and benchmarks](docs/04-test-plan.md)
- [ADR 0001: PostgreSQL-first baseline](docs/adr/0001-postgresql-baseline.md)
- [Postman collection and local environment](postman/README.md)

## Engineering discipline

Never describe the system as handling a million users until a load model, hardware profile, workload generator, experiment artifacts, failure history, and success criteria independently substantiate the claim.
