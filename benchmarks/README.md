# DropCore benchmark artifacts

Store scripts, raw results, machine specifications, and a report per experiment. Each report must include:

- Commit SHA and environment configuration
- Workload definition: virtual clients, arrival rate, unique customers, total requests, event supply
- Test duration, warmup, generator hardware, application hardware, database hardware
- Success definition, error breakdown, throughput and p50/p95/p99 latency
- Reconciliation query output and failure-injection log when used
- Limitation statement: what the result does *not* prove

Initial benchmark harness is intentionally not bundled until the API and correctness suite exist. Recommended future tools: k6 for HTTP load; Testcontainers for integration tests; Toxiproxy or controlled process faults for chaos experiments.
