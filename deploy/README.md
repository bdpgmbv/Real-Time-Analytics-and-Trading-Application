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
| Postgres | 127.0.0.1 | 5432 | `rtat` / whatever you put in `deploy/.env`, database `rtat` |
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

---

## Secrets

Nothing has a working default. A service with no database password stops with a message
that names the variable to set:

```
spring.datasource.password is not set. Set SPRING_DATASOURCE_PASSWORD, or point
SPRING_DATASOURCE_PASSWORD_FILE at a file holding it. To run against the throwaway
local stack instead, start with --spring.profiles.active=local
```

Three ways to supply one, in the order you should prefer them:

| | how | when |
|---|---|---|
| **a mounted file** | `SPRING_DATASOURCE_PASSWORD_FILE=/run/secrets/db` | production. Docker secrets and Kubernetes secrets both mount files. The value never appears in `docker inspect`, `ps`, or a crash dump of the environment |
| an environment variable | `SPRING_DATASOURCE_PASSWORD=...` | acceptable, but visible to anything that can read the process environment |
| the `local` profile | `--spring.profiles.active=local` | your own machine. It does not supply a password — it only permits a throwaway one and turns health detail back on |

Any variable ending `_FILE` is read this way, not only the database password. The trailing
newline your editor leaves is stripped.

**Known development passwords are refused outside the `local` profile** — `rtat_dev_only`,
`password`, `changeme`, `admin`, `postgres`, `secret`. The service will not start. This exists
because a default that works is a default that ships.

Before the first `docker compose up`:

```bash
cp deploy/.env.example deploy/.env
```

Then fill it in. `deploy/.env` is git-ignored.

## What is reachable from outside

| | port | listens on |
|---|---|---|
| upload endpoint | 8090 | all interfaces |
| health, metrics, prometheus | 9191 | **127.0.0.1 only** |
| stream metrics | 9192 | **127.0.0.1 only** |
| Postgres, Kafka, Redis | 5432, 9092, 6379 | **127.0.0.1 only** |
| Prometheus, Grafana | 9090, 3000 | **127.0.0.1 only** |

Actuator is on its own port so that opening 8090 to clients does not also open health,
metrics and environment details. `RTAT_ADMIN_ADDRESS` moves it if a metrics collector runs
on another host; put it behind the network policy, not the internet.

Health detail is **off** by default — the full response names the database product, disk
paths and SSL chains, which is a map for anyone probing. `--spring.profiles.active=local`
turns it back on, or set `RTAT_HEALTH_DETAIL=always`.

Grafana anonymous viewing is **off**, and it will not start without `GRAFANA_PASSWORD`.

---

## Who may call what

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.auth.yml up -d
```

Keycloak on `127.0.0.1:8180`, realm `rtat`, imported from `deploy/auth/rtat-realm.json`.
The API is a plain OAuth2 resource server: it validates the token against the realm's
published keys and does not talk to Keycloak on the request path.

Getting a token the way a UI would:

```bash
curl -s -X POST http://localhost:8180/realms/rtat/protocol/openid-connect/token \
  -d client_id=rtat-ui -d grant_type=password -d username=user11 -d password=user11-password
```

`grant_type=password` is for testing only. The `rtat-ui` client is public with PKCE, so a
real browser uses the authorization code flow and never sees a client secret.

| endpoint | needs |
|---|---|
| `GET /api/funds` | a valid token |
| `GET /api/funds/{id}/exposure` | an entitlement row for that fund |
| `GET /api/funds/{id}/hedges/suggested` | an entitlement row for that fund |
| `POST /api/funds/{id}/hedges` | that row with `can_send_trades` |
| `GET /actuator/health` | nothing, so an operator can check it |

**The token says who you are. The database says what you may see.** Entitlements are read
from `entitlement` on every request and never from a claim, so revoking access takes effect
on the next call rather than when the token expires 15 minutes later.

`rtat.oidc.user-claim` picks which claim identifies the user, defaulting to
`preferred_username` because `app_user.user_id` holds the client's own identifier. Where we
control provisioning, `sub` is the better choice — it survives a rename.

Two things the checks deliberately do:

- **Another company's fund is refused, not quietly emptied.** An empty result would tell an
  attacker the fund exists but is empty. A fund that does not exist is refused with the same
  message as one that does, so probing learns nothing either way.
- **Account lists are narrowed, not rejected.** Passing `?account=` values from another fund
  drops them and answers with what you were allowed to see.

---

## Traces

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.tracing.yml up -d
```

Jaeger on http://localhost:16687. Services send OTLP to `127.0.0.1:4320`.

Metrics say a request was slow. Traces say which part of it was. Every log line carries
the trace id, so a complaint about one slow page leads to the exact spans:

```
%5p [${spring.application.name},%X{traceId:-},%X{spanId:-}]
[rtat-api,2ae964b6e6b0fb2e3776ddd806da5252,702f8ce708c52fc9]
```

`RTAT_TRACE_SAMPLE` is 1.0 here, which records everything. Lower it under real load —
tracing every request at 33,000 rows a second costs more than it tells you.

### What the traces found immediately

The first authenticated request after a restart took **137 ms, of which 100 ms was
`authenticate bearertoken`** — fetching Keycloak's signing keys, lazily, while a client
waited. Every request after it was 7 ms.

`WarmUpTheTokenKeys` now fetches them during startup instead:

| | first request | warm |
|---|---|---|
| before | 137 ms | 7 ms |
| after | 47 ms | 10–16 ms |

The 140 ms did not disappear — it moved to startup, where nobody is waiting for it. This
is the kind of thing metrics alone would have shown as a p99 spike with no cause attached.

---

## Live screens

```
GET /api/funds/{id}/exposure/live      text/event-stream
```

Same entitlement check as the plain endpoint — another company's fund is 403 on the
stream too, before any connection is held open.

**The hard part is not streaming, it is not streaming too much.** Prices arrive at 208 a
second and burst to 4,167. A screen cannot use that and a person cannot read it. So:

```
  kafka topics ──► mark dirty ──► every second ──► recalculate watched funds
                   (a flag, not                    ──► send only if the numbers
                    a queue)                            actually differ
```

Measured over 25 seconds with prices flowing:

| | |
|---|---|
| messages through Kafka | 44,057 |
| SSE events sent | 6 |
| ratio | **7,342 to 1** |
| event rate | 0.24/sec against a 1.00 cap |
| pushes suppressed as unchanged | 5 |

Three things this does deliberately:

- **Nobody watching means no work.** No screens open, nothing is calculated, however much
  moves. The cost is driven by open screens, not by market activity.
- **A dirty flag, not a queue.** Forty-four thousand messages set the same boolean. There
  is nothing to drain and nothing to fall behind on.
- **Unchanged numbers are not resent.** `rtat_live_unchanged_total` counts the suppressed
  pushes, so you can see the ratio in Grafana.

The limit is honest: every watched fund is recalculated each tick at roughly 14 ms
(`rtat_exposure_calculated_seconds`), so one node sustains around 70 concurrently watched
funds per second before the sweep overruns its own interval. Past that, either raise
`RTAT_LIVE_EVERY` or work out which funds a tick actually touched instead of recalculating
all of them.

---

## The exposure job

`jobs/exposure` is a Flink job. It exists because recalculating is the wrong shape of
answer for this problem.

```
rtat.price ──► which funds hold it ──► the difference ──► running total ──► rtat.exposure
               (loaded once from        not the whole      per fund and
                Postgres)               new value          currency
```

Two rules, both from `docs/scale-numbers.md`:

| event | what the job does | what it touches |
|---|---|---|
| a price moves | add the difference to the funds holding it | **2 fund totals** (worst case 3) |
| an FX rate moves | nothing — the base-currency total did not change, only the number it is multiplied by when read | **0** |

Measured on the laptop dataset:

```
  one price tick
    recalculating touches   1,630,802 positions
    the delta job touches           2 fund totals
    ratio                     815,401 to 1
```

The FX row is the one that matters. An FX move is the event the old system could not
survive — 543,000 positions at full scale — and the job does no work for it at all,
because exposure is held in the security's own currency and converted at read time.

Running it:

```bash
RTAT_DB_PASSWORD=... flink run jobs/exposure/build/libs/rtat-exposure-0.1.0-SNAPSHOT.jar
```

### What is proved and what is not

The arithmetic is proved with Flink's own operator harness, including a checkpoint and
restore: after a restart the job sends the difference, not the whole value again, so a
restart cannot double-count. The rolled-up holdings query is proved against a real
Postgres.

**The job has not been run on a Flink cluster.** The operators are tested, the wiring is
not. Two limits are honest and unfixed:

- Holdings are loaded once at startup. A position that changes afterwards is not picked up
  until the job restarts. A real deployment needs the position stream feeding the same job.
- Every task manager holds the whole map — 248,402 rows on the laptop dataset. At full
  scale that is ten times larger and belongs in keyed state fed by a stream, not a
  HashMap in `open()`.

---

## Running this alongside other projects

Twenty projects each with their own Postgres, Kafka and Redis does not fit on a laptop,
and no Docker setting makes it fit. The arithmetic:

```
  this project, local profile
    kafka 1G + postgres 2G + redis 512M                    = 3.5 GB
    prometheus, grafana, jaeger, keycloak                 ~ 1.5 GB
                                                            -------
    one project                                             5 GB
    twenty projects                                       100 GB
    a 16 GB laptop                                         16 GB
```

Four things that actually help, best first.

### 1. One Postgres and one Kafka for every project

```bash
SHARED_DB_PASSWORD=... docker compose -p shared -f deploy/shared/docker-compose.yml up -d
./deploy/shared/new-project.sh rtat
```

One Postgres process holds as many databases as you like. One broker holds as many topics
as you like, and this project already prefixes every topic with `rtat.`. Twenty projects
then cost **4.5 GB once**, not 4.5 GB each.

Point this project at it:

```bash
RTAT_DB_URL=jdbc:postgresql://localhost:5432/rtat RTAT_KAFKA=localhost:9092 ...
```

### 2. Start only the part you are working on

The stack is deliberately in five files, not one. Running all of them is a choice:

| | cost | when you need it |
|---|---|---|
| `docker-compose.yml` + `.local.yml` | 3.5 GB | always |
| `docker-compose.observability.yml` | ~700 MB | measuring something |
| `docker-compose.auth.yml` | ~600 MB | touching the API |
| `docker-compose.tracing.yml` | ~300 MB | chasing a slow request |

### 3. Let the tests share one database

Eleven test classes used to start eleven Postgres containers. They now share one, and
`withReuse(true)` keeps it alive between runs, so the second `./gradlew check` does not
pay for a cold start at all.

Reuse needs one line on the machine, which is deliberately not a repo file — it is a
per-developer choice:

```bash
echo 'testcontainers.reuse.enable=true' >> ~/.testcontainers.properties
```

Without it nothing breaks; containers simply go back to being thrown away each run.

### 4. Put the heavy things on the cloud box

The EC2 instance has 30 GB of memory and 188 GB of disk and is mostly idle. Postgres and
Kafka can live there while you work on the laptop — the SSH tunnel above already does
exactly this, and nothing new is exposed.
