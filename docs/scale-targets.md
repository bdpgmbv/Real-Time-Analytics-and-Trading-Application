# Scale Targets — Legacy vs Modern

Build targets for the rtat replica at full FXAN scale.

Two compounding growth drivers separate the columns:

1. **Clients 120 → 400** (3.3×) — scales clients, funds, accounts, positions
2. **Multi-custodian support** — funds expose externally-custodied accounts that were never in the system before (~+60% accounts per fund)

Legacy column is reconstructed from the architecture and known failure symptoms. It is not measured — do not quote it as fact. Modern column is what this build measures.

---

## 1. Hierarchy cardinality

| Level | Legacy | Modern | Multiple |
|---|---|---|---|
| Client | 120 | 400 | 3.3× |
| Fund | ~360 | ~1,200 | 3.3× |
| Accounts per fund | ~5 (internal only) | ~8 (5 internal + ~3 custodian) | 1.6× |
| Account | ~1,800 | ~9,600 | 5.3× |
| Product | ~150,000 | ~400,000 | 2.7× |
| Position | ~3M | ~16M | 5.3× |
| Currencies | 25 | 30 | |
| Fund base currencies | 6–8 | 6–8 | |

---

## 2. Users and sessions

| | Legacy | Modern |
|---|---|---|
| Named users | ~400 | ~2,000 |
| Concurrent users, peak | ~60 | 200 |
| Sessions per day | ~600 | ~3,500 |
| Peak concurrency window | 08:00–10:00 local | same |

---

## 3. API Gateway

| | Legacy | Modern |
|---|---|---|
| Dedicated gateway | none | Spring Cloud Gateway |
| Request rate, steady | ~30 QPS | 200 QPS |
| Request rate, peak | ~100 QPS | 1,000 QPS |
| Gateway overhead p99 | n/a | < 20ms |
| JWT validation | n/a | < 2ms (cached JWKS) |
| Rate limit — refdata | none | 50/sec per client |
| Rate limit — query | none | 200/sec per client |
| Rate limit — hedge | none | 10/sec per client |
| Circuit breaker window | none | 50 calls, opens at 50% failure |
| Connect timeout | default | 2s |
| Response timeout | default | 10s |
| Max request header | default | 16KB |
| Max in-memory body | default | 256KB |

---

## 4. Microservices

| Service | Instances | Throughput | p99 | Heap |
|---|---|---|---|---|
| refdata | 2 | 100 QPS | 50ms | 2GB |
| price | 4 | 5,000 resolutions/sec | 10ms | 4GB |
| query | 6 | 200 QPS | 300ms | 4GB |
| hedge | 2 | 50 QPS | 1s | 2GB |
| order | 2 | 200/sec peak | 500ms | 2GB |
| bff | 4 | 200 SSE connections | 100ms | 2GB |
| gateway | 3 | 1,000 QPS peak | 20ms | 2GB |

Legacy: one monolith, vertical scaling only, no per-component isolation.

---

## 5. Kafka

| | Legacy | Modern |
|---|---|---|
| Brokers | 3 | 3 |
| Keying | none / inconsistent | account_id |
| Hot partitions | yes | eliminated |
| Replication factor | 2 | 3 |
| min.insync.replicas | 1 | 2 |
| Retention | 1 day | 7 days |
| Peak message rate | ~200/sec | ~19,000/sec |
| Peak byte rate | ~60KB/sec | ~5.7MB/sec |
| Daily volume | ~1GB | ~10GB |
| On-disk (7d × RF3) | ~3GB | ~210GB |

### Topic partition plan (modern)

| Topic | Partitions | Accounts/partition |
|---|---|---|
| rtat.positions.eod | 128 | ~75 |
| rtat.positions.intraday | 64 | ~150 |
| rtat.trades | 32 | |
| rtat.prices | 64 | |
| rtat.fxrates | 16 | |
| rtat.exposures | 64 | |
| rtat.hedges | 16 | |
| rtat.orders | 16 | |
| rtat.fills | 16 | |
| rtat.dlq.* | 8 each | |

Consumer lag alert: > 30s. Consumer lag page: > 5 min.

---

## 6. Flink

| | Legacy | Modern |
|---|---|---|
| Stream processing | none | 6 jobs |
| Parallelism | n/a | 32 |
| TaskManagers | n/a | 8 × 4 slots |
| Memory per TM | n/a | 8GB (64GB total) |
| Keyed state | n/a | ~15GB RocksDB |
| Broadcast state (refdata) | n/a | ~80MB |
| Checkpoint interval | n/a | 60s |
| Checkpoint duration | n/a | < 10s (incremental) |
| Savepoint duration | n/a | < 2 min |
| Watermark lag | n/a | < 5s |
| Allowed lateness | n/a | 30s |
| Recovery from TM loss | n/a | < 60s |
| Restart strategy | n/a | exponential backoff, 10 attempts |

### Jobs

| Job | Key | Parallelism | State |
|---|---|---|---|
| position-loader | account_id | 32 | small |
| valuation | (account_id, product_id) | 32 | ~12GB |
| exposure | (fund_id, ccy) | 16 | ~1GB |
| hedge-calc | (fund_id, ccy) | 8 | small |
| forward-maturity | (fund_id, ccy, value_date) | 8 | timers |
| fill-processor | order_id | 8 | small |

---

## 7. Database

| | Legacy | Modern |
|---|---|---|
| Engine | DB2-DPF | PostgreSQL 16 |
| Current positions | 3M rows | 16M rows |
| Partitioning | limited | by account range |
| Roll-up query pattern | full scan | indexed + partition pruning |
| Query p99 | 5–30s | < 50ms |
| Connection pool | ~20, exhausted at open | 100, headroom to 200 |
| Pool exhaustion incidents | frequent at market open | zero |
| Write pattern | row-at-a-time | batched, 1,000 rows |
| Pagination | offset (degrades) | keyset |
| Operational storage | ~1.5GB | ~8GB |
| Refdata storage | ~0.5GB | ~2GB |

---

## 8. Cache

| | Legacy | Modern |
|---|---|---|
| Cache tier | none | Redis 7 |
| Size | n/a | 4GB |
| Ops/sec | n/a | 10,000 |
| p99 | n/a | < 2ms |
| Refdata hit rate | n/a | > 95% |
| Contents | n/a | hot aggregates, rate-limit counters, session |

---

## 9. Ingestion — internal feed

| | Legacy | Modern |
|---|---|---|
| Internal accounts | 1,800 | ~6,000 |
| Positions from internal | 3M | ~12M |
| Load pattern | sequential | parallel |
| Accounts loaded per second | ~0.1 | 5 |
| Throughput | ~165 positions/sec | ~10,000/sec |
| EOD window | 4–6 hours | < 20 min |
| Snapshot completeness tracking | none | BEGIN/COMPLETE markers with expected count |
| Partial-load detection | none | automatic |
| Failed load recovery | manual rerun, hours | automatic replay, < 5 min |

---

## 10. Ingestion — custodian files

| | Legacy | Modern |
|---|---|---|
| Custodians supported | 0 (manual upload only) | 15–25 |
| External accounts | ~0 | ~3,600 |
| Positions from files | negligible | ~4M/night |
| Files per night | a handful, manual | ~800 |
| Rows per file | — | ~5,000 |
| Arrival window | — | 6-hour staggered spread |
| Pre-flight validation | none | < 2s for 50k rows |
| Validation throughput | — | 4M rows/night |
| Duplicate detection | none | file hash + record key |
| Reconciliation vs internal | manual | automated, nightly |
| Malformed file handling | partial load | quarantine, never partial |
| Late/missing file alert | none | within 30 min of expected |
| New custodian onboarding | code release | configuration change |

---

## 11. Pricing

| | Legacy | Modern |
|---|---|---|
| Distinct products priced | ~150,000 | ~400,000 |
| Price cycle | 20 min | 20 min |
| Price rate, average | ~125/sec | ~330/sec |
| Price rate, burst | ~2,500/sec | ~5,000/sec |
| Resolution tiers | 4 | 4 |
| Resolution latency p99 | 5–20ms (DB) | < 10ms (cached) |
| Staleness detection | none | flagged above threshold |
| Missing price handling | valued at zero silently | flagged, excluded from hedge |
| FX pairs | ~200 | ~200 |
| FX rate updates | ~50/sec | ~200/sec |

---

## 12. Exposure calculation

| | Legacy | Modern |
|---|---|---|
| Aggregation keys (fund × ccy) | ~9,000 | ~36,000 |
| Recompute model | full portfolio | incremental keyed state |
| Positions revalued per price tick | 3M (full sweep) | ~40 |
| Peak revaluation rate | ~100k/sec then stall | 200,000/sec sustained |
| Trade → visible exposure | next refresh | < 2s |
| Price tick → revalued MV | next refresh | < 5s |
| Generic + specific weight handling | manual config | streaming, versioned |

---

## 13. Hedge calculation

| | Legacy | Modern |
|---|---|---|
| Fund × currency combinations | ~9,000 | ~36,000 |
| Calculation trigger | on demand | continuous + on demand |
| Latency p99 | seconds | < 1s |
| Hedge suggestions per day | ~2,000 | ~12,000 |
| Instrument types | spot, forward, swap | spot, forward, swap |

---

## 14. Forward maturity

| | Legacy | Modern |
|---|---|---|
| Outstanding forwards | ~150,000 | ~800,000 |
| Maturity check | daily batch | Flink timers, continuous |
| Alert lead time | day-of | T-3 days |
| Roll generation | manual, 6 min/ticket | paired draft, automatic |

---

## 15. Cash management

| | Legacy | Modern |
|---|---|---|
| Cash balance keys (account × ccy) | ~54,000 | ~288,000 |
| Refresh | on demand | continuous |
| Zero-down calculation | manual | automatic |

---

## 16. Share class

| | Legacy | Modern |
|---|---|---|
| Share classes | ~1,400 | ~4,800 |
| Hedged share classes | ~400 | ~1,440 |
| Net exposure recompute | daily | continuous |

---

## 17. Trade execution

| | Legacy | Modern |
|---|---|---|
| Trades per day | ~150,000 | ~500,000 |
| Peak trade rate | ~50/sec | ~200/sec |
| Order submission p99 | ~2s | < 500ms |
| Duplicate submissions | occurred on retry | zero (idempotency keys) |
| Fill processing | batch | streaming |
| Out-of-order fill handling | none | sequence-corrected |

---

## 18. UI and grid

| | Legacy | Modern |
|---|---|---|
| Concurrent users | ~60 | 200 |
| Grid query rate | ~30 QPS | 200 QPS |
| Grid query p99 | 5–30s, or hang | < 300ms |
| Initial render | 5–20s | < 1s |
| Rendering model | full re-render | virtualized + delta updates |
| Rows held in browser | all returned rows | ~100 visible, virtualized |
| Tree expand | 1–5s | < 200ms |
| Filter / sort | 2–10s | < 500ms |
| Live update push | manual refresh | SSE, < 1s |
| Browser OOM on large funds | frequent | none |
| Export (CSV/Excel) | timed out on large funds | streamed, no limit |

---

## 19. Storage and retention

| | Legacy | Modern |
|---|---|---|
| Daily snapshot rows | 3M | 16M |
| Retention | 7 years | 7 years |
| Total snapshot rows | ~5.25B | ~28B |
| Compressed size | ~1TB | ~5.6TB |
| Format | relational | Parquet + ZSTD |
| Point-in-time query | manual log archaeology, hours | bounded query |
| Regulatory basis | SEC 17a-4 | SEC 17a-4 |

---

## 20. Observability

| | Legacy | Modern |
|---|---|---|
| Metrics | limited | full RED per service |
| Distributed tracing | none | OpenTelemetry, 5% sampling, 100% on error |
| Correlation IDs | none | request → gateway → service → Flink |
| Log volume | ~2GB/day | ~15GB/day structured |
| Alert on consumer lag | none | > 30s warn, > 5 min page |
| Alert on checkpoint failure | n/a | immediate |
| Alert on stale pricing | none | > 25 min |
| Alert on missing custodian file | none | 30 min past expected |
| Mean time to detect | client reports it | < 2 min |

---

## 21. Reliability and DR

| | Legacy | Modern |
|---|---|---|
| Availability, market hours | unmeasured | 99.9% |
| RPO | hours | 0 |
| RTO | hours | < 5 min |
| Failure recovery | full batch rerun | checkpoint restore < 60s |
| Backpressure | none — queues grew unbounded | propagated to source |
| Circuit breakers | none | per downstream |
| DLQ | none | per topic |
| Graceful shutdown | none | 25s drain |
| Tenant isolation | none | per-account keying + per-client quota |

---

## 22. Security

| | Legacy | Modern |
|---|---|---|
| Authentication | session-based | OIDC / JWT |
| Entitlement check rate | per page load | 200/sec |
| Entitlement latency | in-request DB | < 5ms cached |
| Transport | mixed | TLS everywhere, mTLS internal |
| Secrets | config files | Vault, 90-day rotation |
| Audit trail | partial | immutable, all overrides and orders |
| Market data licensing | manual | enforced per exchange (70+ agreements) |

---

## 23. Build and deployment

| | Legacy | Modern |
|---|---|---|
| Build time | ~30 min | < 10 min |
| Test suites | unit only, partial | unit, integration, contract, load |
| Coverage gate | none | 80% line / 70% branch |
| Deployment | manual, coordinated | CI/CD, blue-green |
| Deployment duration | hours, out-of-hours | < 5 min |
| Rollback | redeploy previous, hours | < 2 min |
| Deployment frequency | monthly | on demand |
| Containerization | none | all services |
| Orchestration | none | Kubernetes + Flink operator |

---

## 24. Infrastructure footprint

| | Legacy | Modern |
|---|---|---|
| Application cores | ~32 | ~96 |
| Application memory | ~128GB | ~320GB |
| Flink cores | 0 | 64 |
| Flink memory | 0 | 64GB |
| Database storage | ~50GB | ~120GB |
| Object storage | ~1TB | ~5.6TB |
| Kafka storage | ~3GB | ~210GB |

Data handled per core: legacy ~94k positions/core, modern ~167k positions/core — **1.8× better density despite 5.3× the data.**

---

## 25. Headline comparison

| Metric | Legacy | Modern | Gain |
|---|---|---|---|
| Positions | 3M | 16M | 5.3× |
| Accounts | 1,800 | 9,600 | 5.3× |
| Ingest throughput | ~165/sec | ~13,300/sec | ~80× |
| EOD wall clock | 4–6 hrs | < 20 min | ~15× |
| Grid query p99 | 5–30s | < 300ms | 20–100× |
| Work per price tick | 3M revaluations | ~40 | ~75,000× |
| Exposure freshness | next-day | < 2s | batch → real-time |
| Recovery from failure | hours | < 60s | ~100× |
| Position sources | 1 | 16–26 | new capability |
| Positions per core | ~94k | ~167k | 1.8× |

---

## Measurement checklist

Record each with the method used. These become defensible resume numbers.

- [ ] Positions/sec sustained on EOD load; wall clock for 16M
- [ ] Grid query p99 at 200 concurrent users
- [ ] Trade-to-exposure latency, p50 / p99 / p999
- [ ] Peak revaluations/sec before backpressure engages
- [ ] Checkpoint duration and state size at 16M positions
- [ ] Recovery time after killing a TaskManager mid-load
- [ ] Consumer lag under peak producer rate
- [ ] Pre-flight validation time for a 50k-row custodian file
- [ ] Cold start to first served query
- [ ] Infrastructure footprint — cores, GB RAM, GB storage
- [ ] Cost per million events, if run on cloud
