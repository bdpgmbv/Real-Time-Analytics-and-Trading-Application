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
