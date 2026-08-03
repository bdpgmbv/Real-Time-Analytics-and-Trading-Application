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

```
common/        domain primitives (money, currency) - no framework dependencies
contracts/     protobuf schemas, the shared vocabulary
edge/          API gateway
services/      request/response services (Spring Boot)
streaming/     Flink jobs, one per pipeline
simulators/    stand-ins for upstream systems
testing/       shared test fixtures
deploy/        docker compose, helm, flink operator manifests
tools/         load-test scripts
docs/          ADRs and runbooks
```

## Build

```bash
./gradlew checkAll
```

Requires JDK 21. The Gradle wrapper pins the build to Gradle 9.3.
