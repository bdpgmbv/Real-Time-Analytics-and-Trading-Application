# Running the stack

Kafka, Postgres and Redis. One definition, two sizes.

---

## On the cloud box — full size

```bash
cd deploy
docker compose up -d
docker compose ps
```

Expect three services reporting `healthy`. Sized for a 32 GB machine: Kafka 4 GB, Postgres 8 GB, Redis 2 GB.

---

## On a laptop — reduced size

```bash
cd deploy
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d
```

Same services, same versions, same configuration shape. Only the memory limits and Postgres tuning change: Kafka 1 GB, Postgres 2 GB, Redis 512 MB.

Use this for quick checks — does the code compile, does it connect, does one message round-trip. Not for measuring anything. **Every number that matters is measured on the cloud box**, because a memory-starved Postgres produces misleading timings.

---

## Making the data — two sizes

The laptop is the default. You have to ask for the full set on purpose.

```bash
docker exec -i rtat-postgres psql -U rtat -d rtat < db/1-schema.sql
docker exec -i rtat-postgres psql -U rtat -d rtat < db/2-reference.sql
docker exec -i rtat-postgres psql -U rtat -d rtat < db/3-generate.sql
docker exec -i rtat-postgres psql -U rtat -d rtat < db/4-indexes.sql
docker exec -i rtat-postgres psql -U rtat -d rtat < db/5-verify.sql
```

On the cloud box, pass `where=cloud` to step 3 and nothing else changes:

```bash
psql -U rtat -d rtat -v where=cloud -f db/3-generate.sql
```

| | laptop (default) | cloud (`-v where=cloud`) |
|---|---|---|
| clients | 40 | 400 |
| funds | 132 | 1,320 |
| accounts | 1,044 | 10,440 |
| **positions** | **1,630,800** | **16,308,000** |
| position_exposure | 16,805 | 168,099 |
| products | 1,050,060 | 1,050,060 |
| prices | 1,050,060 | 1,050,060 |
| database on disk | 395 MB | 1,761 MB |
| generate step | 12s | 45s |
| index step | 4s | 23s |

**Products and prices do not shrink.** The product universe is shared — thousands of funds hold the same Airbus row. Scaling it down would change the shape of every join and make laptop timings meaningless in a way the position count does not. Only the client tree gets smaller.

The shape that matters survives the cut. On both sizes the large clients hold **71.7%** of positions, and every account still carries exactly 400, 1,000 or 2,500 rows by client size — so `5-verify.sql` passes unchanged at either size.

---

## Reaching the cloud services from a laptop

The security group opens SSH only. Postgres and Kafka are not exposed to the internet and must not be.

Forward the ports over the existing SSH connection instead:

```bash
ssh -i ~/.ssh/rtat-key.pem -N \
  -L 5432:localhost:5432 \
  -L 9092:localhost:9092 \
  ubuntu@<instance-ip>
```

Leave it running. `localhost:5432` on the laptop is now the cloud Postgres, `localhost:9092` the cloud Kafka. Traffic travels inside the SSH tunnel, already encrypted, and nothing new is opened to the internet.

One caveat for Kafka: the broker advertises `localhost:9092` on its external listener, which is exactly what a tunnelled client needs. That is not a coincidence — it is why the listener is configured that way rather than with the instance's public address.

---

## Connection details

| Service | Host | Port | Credentials |
|---|---|---|---|
| Kafka | localhost | 9092 | none |
| Postgres | localhost | 5432 | `rtat` / `rtat_dev_only`, database `rtat` |
| Redis | localhost | 6379 | none |

No authentication, no TLS. Acceptable because nothing is exposed beyond SSH. Both get added when the gateway returns.

---

## Stopping

```bash
docker compose down
```

Containers stop, named volumes survive. Data is still there next time.

```bash
docker compose down -v
```

Deletes the volumes too. Kafka's volume must be removed after changing listener names, because those are recorded in the cluster metadata and a stale copy will override the new configuration.

---

## Watching it run

```bash
docker compose -f deploy/docker-compose.observability.yml up -d
```

| | where | what |
|---|---|---|
| Grafana | http://localhost:3000 | dashboards, anonymous viewer |
| Prometheus | http://localhost:9090 | raw queries and alert state |
| ingest metrics | http://localhost:8090/actuator/prometheus | |
| stream metrics | http://localhost:8091/actuator/prometheus | |

Metrics the alerts depend on:

| metric | what it tells you |
|---|---|
| `rtat_rows_changed_total{feed}` | rows the database actually changed, not messages read |
| `rtat_batch_write_seconds{feed}` | how long one batch took, with percentiles |
| `rtat_batch_failed_total{feed}` | writes that threw, per feed |
| `rtat_messages_sent_total{topic}` | producer side, per topic |
| `rtat_messages_failed_total{topic}` | producer failures — the alert watches this |
| `rtat_file_rows_loaded_total` | custodian rows accepted |
| `rtat_file_rows_rejected_total` | custodian rows refused |
| `hikaricp_connections_pending` | threads queueing for a database connection |

`rtat_rows_changed_total` counts rows, not messages, on purpose. An earlier version
counted messages read and reported success for four hours a night while the FX rate
writer was matching zero rows.

## Running a sender

```bash
java -jar stream/build/libs/rtat-stream-0.1.0-SNAPSHOT.jar --rtat.send=position
```

`position`, `price`, `rate`, `trade`, `hedge-fill`. Naming none of them lists them.

## Tuning

| setting | default | why |
|---|---|---|
| `RTAT_LISTENER_THREADS` | 3 | one per partition. At 1 it was 10,157 rows/sec, at 3 it is 33,284 |
| `RTAT_DB_POOL` | 24 | must exceed listener threads × feeds, or they queue |

Raising threads above the partition count does nothing — Kafka gives a partition to
one consumer. Ordering per account survives because messages are keyed on `accountId`,
so an account always lands on the same partition and therefore the same thread.
