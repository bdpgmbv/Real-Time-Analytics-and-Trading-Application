# Build Log

Everything actually done, in order, with real output. Written so the whole environment can be rebuilt from nothing, and so every claim about it can be checked.

---

# Part 1 — Why a cloud machine at all

The development laptop was measured first:

| | |
|---|---|
| Chip | Apple M3, 8 cores |
| RAM | 16 GB |
| Docker allocation | ~11 GB |
| Free disk | **17 GB** of 228 GB |

Against the targets in [scale-numbers.md](scale-numbers.md):

| Need | Laptop has | |
|---|---|---|
| Flink state ~16 GB | 11 GB Docker RAM | ❌ |
| Disk ~64 GB live | 17 GB free | ❌ |
| 14 cores | 8 | tight |

**Disk was the binding constraint, not RAM.** Conclusion: build on a rented machine.

**No GPU.** The project has no machine learning. Kafka, Flink and Postgres are bound by CPU, memory and disk. A GPU would sit idle.

---

# Part 2 — Choosing the machine

Prices checked at the time, not assumed:

| | AWS r6i.2xlarge | Hetzner CCX43 |
|---|---|---|
| Specs | 8 vCPU, 64 GB | 16 vCPU, 62 GB |
| Per hour running | $0.504 | $0.507 |
| **When stopped** | **$0 compute** | **still billed** |

Hetzner bills while the server exists, even powered off. AWS bills compute only while running. Since this machine runs a few hours a day, that difference decided it.

**Chosen:** `r6i.xlarge` — 4 vCPU, 32 GB, $0.252/hour — for daily work, resizable to `r6i.2xlarge` for full-scale runs. One 200 GB gp3 disk, shared across both sizes.

---

# Part 3 — AWS account setup

Done through the console, in this order.

**1. Account.** Signed up at aws.amazon.com. Chose the **Paid** plan, not Free.

> Both plans give the same $200 credits. Free restricts access to some services and **closes the account automatically after 6 months**. Paid keeps working and only charges once credits are gone.

Support plan: **Basic** (free).

**2. Budget.** Billing and Cost Management → Budgets → monthly cost budget, $10, email alert. Verified showing `$0.00 used`, health `Healthy`.

**3. MFA.** Account menu → Security credentials → Assign MFA device → authenticator app. Done before creating any resources.

**4. Region.** `us-east-1` (N. Virginia). The account defaulted to `us-east-2`; changed deliberately and kept there. Mixing regions is how forgotten, billed instances happen.

**5. Key pair.** EC2 → Key Pairs → Create → `rtat-key`, RSA, `.pem`.

Locked down on the Mac:

```bash
mkdir -p ~/.ssh && mv ~/Downloads/rtat-key.pem ~/.ssh/ && chmod 400 ~/.ssh/rtat-key.pem && ls -l ~/.ssh/rtat-key.pem
```

```
-r--------@ 1 vyshaliprabananthlal  staff  1678 Aug  6 11:53 /Users/.../rtat-key.pem
```

`-r--------` is required. SSH refuses a key file others can read.

**6. Security group.** EC2 → Security Groups → Create.

| | |
|---|---|
| Name | `rtat-sg` |
| Inbound | SSH, port 22, source **My IP** → `70.111.197.108/32` |
| Outbound | All traffic, `0.0.0.0/0` — left as-is |

Source is a single IP, not `0.0.0.0/0`. Outbound stays open because the machine must reach Docker Hub, the SEC and the ECB.

**7. Instance.**

| | |
|---|---|
| Name | `rtat` |
| Image | Ubuntu Server, 64-bit x86 |
| Type | `r6i.xlarge` |
| Key pair | `rtat-key` |
| Security group | `rtat-sg` |
| Storage | 200 GiB gp3 |

---

# Part 4 — First connection

```bash
ssh -i ~/.ssh/rtat-key.pem ubuntu@54.87.216.148
```

```
Welcome to Ubuntu 26.04 LTS (GNU/Linux 7.0.0-1006-aws x86_64)

  System load:  0.44
  Usage of /:   1.0% of 192.85GB
  Memory usage: 1%
  IPv4 address for ens5: 172.31.38.59
```

Ubuntu **26.04**, newer than planned. Accepted — nothing depended on 24.04.

---

# Part 5 — Installing Docker and Java

```bash
sudo apt update && sudo apt upgrade -y
```

**This dropped the SSH connection partway through:**

```
Read from remote host 54.87.216.148: Connection reset by peer
client_loop: send disconnect: Broken pipe
```

Cause: the upgrade restarted SSH or rebooted the machine. Not a failure, but it can leave `apt` half-configured. Reconnected and finished cleanly:

```bash
sudo dpkg --configure -a && sudo apt update && sudo apt upgrade -y
```

Then Docker:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
```

> Docker's own installer script was used rather than the manual apt-repository steps. On a brand-new Ubuntu release the Docker repository often has no packages for that codename yet; the script handles the detection.

Then Java and tools:

```bash
sudo apt install -y openjdk-21-jdk git unzip
```

Logged out and back in so the `docker` group applied, then verified:

```bash
docker --version && docker compose version && java -version
```

```
Docker version 29.7.2, build a7dcaa6
Docker Compose version v5.4.0
openjdk version "21.0.11" 2026-04-21
OpenJDK Runtime Environment (build 21.0.11+10-1-26.04.2-Ubuntu)
```

Java 21.0.11 — the same version as the Mac, so builds behave identically.

---

# Part 6 — Choosing image versions

Queried Docker Hub rather than guessing:

| Image | Available | Chosen | Why |
|---|---|---|---|
| `apache/kafka` | 4.3.1, 4.3.0, 4.2.1 | **4.3.1** | latest stable; KRaft, no ZooKeeper |
| `postgres` | 18.4, 17.10, 16.14 | **17.10** | 17 is battle-tested; 18 is recent |
| `redis` | 8.10.0, 8.8.1 | **8.10** | latest stable |

Every tag pinned. Never `:latest` — that means a different image tomorrow.

---

# Part 7 — The Compose file

Written to `~/rtat/docker-compose.yml`, 98 lines. Source of truth is [`deploy/docker-compose.yml`](../deploy/docker-compose.yml).

Deliberate choices:

| Choice | Reason |
|---|---|
| Kafka KRaft mode | ZooKeeper is removed in Kafka 4 |
| Health checks on all three | nothing starts before its dependency actually answers |
| Named volumes | data survives `docker compose down` |
| Memory limits per container | one service cannot eat the 32 GB box |
| Postgres `shared_buffers=2GB`, `work_mem=64MB`, `max_connections=200` | defaults assume a laptop toy, not 16M rows |
| Postgres `random_page_cost=1.1` | the disk is SSD; the default of 4.0 assumes spinning platters and discourages index use |
| Redis `maxmemory-policy allkeys-lru` | cached aggregates are disposable; eviction is preferable to an out-of-memory kill |
| Replication factor 1 | single broker — see the honest note below |

**Not production shape, and not pretended to be.** Real Kafka is three brokers with replication factor 3 across availability zones. Three brokers on one machine would triple the memory and teach nothing about replication. The settings that change are named, not hidden.

Started:

```bash
cd ~/rtat && docker compose up -d
```

```
Volume rtat_postgres-data Created
Volume rtat_kafka-data Created
Network rtat_default Created
Container rtat-postgres Started
Container rtat-redis Started
Container rtat-kafka Started
```

```bash
docker compose ps
```

```
SERVICE    STATUS
kafka      Up 50 seconds (healthy)
postgres   Up 50 seconds (healthy)
redis      Up 50 seconds (healthy)
```

`healthy`, not merely `running` — Kafka answered an API call, Postgres answered `pg_isready`, Redis answered `ping`.

---

# Part 8 — What broke, on purpose

The Kafka listener configuration was written wrong first, to make the failure visible rather than described.

**The wrong line:**

```yaml
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
```

**Test 1 — from inside the Docker network:**

```bash
docker exec rtat-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
```

Worked. Exit code 0.

**Test 2 — from the host, where application code runs:**

```bash
docker run --rm --network host apache/kafka:4.3.1 \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

```
WARN [AdminClient clientId=adminclient-1] Error connecting to node kafka:9092
java.net.UnknownHostException: kafka: Try again
	at java.base/java.net.Inet6AddressImpl.lookupAllHostAddr(Unknown Source)
```

**Why.** A Kafka client is redirected. It connects to the bootstrap address, and Kafka replies with the address from `advertised.listeners` for all subsequent traffic. That value was `kafka:9092` — a name only resolvable inside the Docker network. The port was published correctly; the redirect target was not reachable.

This is why the usual first guess — "the port isn't mapped" — is wrong. The port *is* mapped. That is what makes it confusing.

---

# Part 9 — The fix

Two listeners on separate ports, each advertising an address valid for its caller.

```yaml
KAFKA_LISTENERS: INTERNAL://:29092,EXTERNAL://:9092,CONTROLLER://:9093
KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:29092,EXTERNAL://localhost:9092
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT
KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
```

| Door | Port | Advertised as | Used by |
|---|---|---|---|
| INTERNAL | 29092 | `kafka:29092` | other containers |
| EXTERNAL | 9092 | `localhost:9092` | code on the host |
| CONTROLLER | 9093 | — | KRaft quorum |

Applied, then Kafka's volume was removed and the stack restarted, because listener names are recorded in the cluster metadata:

```bash
docker compose down
docker volume rm rtat_kafka-data
docker compose up -d
```

---

# Part 10 — Verifying the fix

Same two tests, run again.

**Test 1 — inside the Docker network:**

```bash
docker exec rtat-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server kafka:29092 --create --topic rtat.smoke --partitions 3 --replication-factor 1
docker exec rtat-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:29092 --list
```

```
Created topic rtat.smoke.
rtat.smoke
```

**Test 2 — from the host:**

```bash
docker run --rm --network host apache/kafka:4.3.1 \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

```
rtat.smoke
```

| Test | Before | After |
|---|---|---|
| Inside Docker | ✅ worked | ✅ worked |
| From the host | ❌ `UnknownHostException` | ✅ `rtat.smoke` |

Same broker, same topic, reached two ways.

**One warning worth keeping:**

```
WARNING: topics with a period ('.') or underscore ('_') could collide
```

Kafka flattens both characters in metric names, so `rtat.smoke` and `rtat_smoke` would collide in monitoring. All topic names in this project use dots only.

---

# Part 11 — Where the code lives

Writing directly onto the box was a mistake, caught and corrected. It leaves no git history, nothing to push to GitHub, and loses everything if the machine is destroyed.

**Correct flow:**

```
Mac (git repo)  →  push  →  GitHub  →  pull  →  EC2 box
     ↑
   edit here
```

The box only ever receives code. It is disposable: destroy it, launch another, `git clone`, carry on.

`docker-compose.yml` was copied back into the repository at `deploy/docker-compose.yml` and committed.

---

# Part 12 — Running costs

| State | Per hour | Per day if left on |
|---|---|---|
| Running `r6i.xlarge` | $0.252 | ~$6 |
| Stopped | $0 compute | ~$0.55 (200 GB disk) |

**Stop the instance when finished.** EC2 → Instances → select → Instance state → Stop.

Stopping keeps the disk and all data. **Terminating destroys everything.**

The public IP changes on every stop/start. An Elastic IP avoids that — free while attached to a running instance.

For full 16.3M runs: stop, Actions → Instance settings → Change instance type → `r6i.2xlarge`, start. Same disk, twice the power, twice the hourly rate. Change back afterwards.

---

# Rebuilding from scratch

Everything above reduces to:

1. Launch `r6i.xlarge`, Ubuntu, 200 GB gp3, security group allowing SSH from your IP
2. `curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker ubuntu`
3. `sudo apt install -y openjdk-21-jdk git`
4. `git clone <repo> && cd rtat/deploy && docker compose up -d`
5. `docker compose ps` — expect three `healthy`

Roughly ten minutes.
