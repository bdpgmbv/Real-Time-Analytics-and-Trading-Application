# realtimeanalyticsandtrading

A real-time FX exposure and hedging platform: streaming valuation, currency exposure
calculation, auto-derived hedges, and trade execution. Ground-up rebuild of a legacy
fund-services tool.

Short prefix used throughout for artifacts, Kafka topics and database schemas: **`rtat`**.

## Stack

| Concern | Choice |
|---|---|
| Language / build | Java 21, Gradle 9 (multi-module, version catalog) |
| Stream processing | Apache Flink 2.2.1, DataStream API, RocksDB state |
| Messaging | Kafka + Schema Registry, Protobuf contracts |
| Services | Spring Boot 4.0.7, Spring Cloud 2025.1.2 |
| Edge | Spring Cloud Gateway, Redis token-bucket rate limiting |
| Storage | PostgreSQL (JdbcTemplate, Flyway), Redis, MinIO |
| Observability | OpenTelemetry, Prometheus, Grafana, Loki |
| Testing | JUnit 6, AssertJ, Testcontainers, Flink MiniCluster |

Versions are pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). Two pins are
deliberate and should not be bumped casually:

- **Flink 2.2.1, not 2.3.0** - `flink-connector-kafka` 5.0.0-2.2 targets the 2.2 line.
- **Spring Boot 4.0.7, not 4.1.0** - `spring-cloud-dependencies` 2025.1.2 declares 4.0.7.

## Layout

Folders are grouped by how a thing runs, not by what language it is written in.

```
common/        library - money and currency, no framework dependencies
contracts/     library - protobuf schemas, the shared wire vocabulary
apps/          runs as a service (Spring Boot)      - gateway
jobs/          runs on a Flink cluster              - empty for now
sim/           fake upstream feeds, never deployed  - empty for now
architecture/  rules enforced as tests across every module
build-logic/   shared build configuration
deploy/        docker compose
tools/         buf, k6 load tests
docs/          agreed scale numbers, interview questions
```

Keeping `apps`, `jobs` and `sim` separate matters: a Flink job cannot be deployed like a
web service, and a simulator must never reach production at all.

## Scale

Target volumes are in [docs/scale-numbers.md](docs/scale-numbers.md) — 400 clients,
1,320 funds, 10,440 accounts, 16.3M positions. Each figure is broken into small, medium
and large client tiers rather than averaged, because 10% of clients hold 72% of the
positions and evenly-spread test data hides every problem worth finding.

## Build

```bash
./gradlew checkAll
```

Requires JDK 21. The Gradle wrapper pins the build to Gradle 9.3.
