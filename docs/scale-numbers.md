# rtat Scale Numbers — Agreed, One at a Time

Numbers confirmed with the user, in order. Nothing here is an average — every quantity is broken into the small / medium / large tiers the data generator will actually produce.

This document supersedes `scale-targets.md` for any number that appears in both. Entries are added only after the user approves them.

---

## Number 1 — 400 clients

A client is one hedge fund company. Example: *Helikon Investments*.

| Size | How many |
|---|---|
| Small | 240 |
| Medium | 120 |
| Large | 40 |
| **Total** | **400** |

Storage: 400 × 100 bytes = **40 KB**. Nothing.

---

## Number 2 — 1,320 funds

A fund is a pot of money with its own name and its own base currency.

| Client size | Funds each | Clients | Funds |
|---|---|---|---|
| Small | 2 | 240 | 480 |
| Medium | 4 | 120 | 480 |
| Large | 9 | 40 | 360 |
| **Total** | | **400** | **1,320** |

Storage: 1,320 × 150 bytes = **198 KB**. Still nothing.

Note the shape: 40 large clients hold 360 funds. **10% of clients, 27% of the funds.** That skew is what will hurt us later.

---

## Number 3 — 10,440 accounts

An account is where positions actually sit. One fund holds several — one per prime broker or custodian.

| Client size | Internal per fund | External per fund | Funds | Internal | External |
|---|---|---|---|---|---|
| Small | 3 | 1 | 480 | 1,440 | 480 |
| Medium | 5 | 3 | 480 | 2,400 | 1,440 |
| Large | 7 | 6 | 360 | 2,520 | 2,160 |
| **Total** | | | **1,320** | **6,360** | **4,080** |

**10,440 accounts total. 6,360 internal, 4,080 external.**

Storage: 10,440 × 200 bytes = **2 MB**.

**39% of all accounts are external.** Not a side feature.

### Why the internal / external split matters

The two kinds arrive completely differently:

| | Internal | External |
|---|---|---|
| Source | Our own accounting system | 15–25 outside custodians |
| How it arrives | Streaming, all day | Files |
| Format | One, ours | Different for every custodian |
| Can it be late or broken? | Rarely | Often |

### Three arrival paths for the 4,080 external accounts

External data is **not** an overnight batch.

| Path | Accounts | When | User waiting? |
|---|---|---|---|
| SFTP overnight | 2,856 | Scheduled, quiet hours | No |
| SFTP intraday | 816 | Any time, unscheduled | No |
| Manual UI upload | 408 | Any time, market hours | **Yes** |

That last row is the hard one.

A client uploads a file at 10am — the middle of the trading day. The system is already busy pricing and calculating exposure. And a human is sitting there watching a spinner.

So the upload path needs things the nightly path doesn't:

- **Fast validation.** Tell them it's bad in seconds, not tomorrow morning.
- **No full reload.** One account's file arriving must not restart anything else.
- **Idempotency.** They will upload the same file twice. Guaranteed.
- **Backpressure.** A 200 MB upload at market open cannot starve live pricing.

This also kills a simplification: there is no quiet window where all position loading happens. Positions arrive **all day, from three directions.**

### Consequence for Kafka

Every position message is keyed by account. 10,440 accounts spread over 128 partitions = about 82 accounts per partition.

---

## Number 4 — 16,308,000 positions

| Client size | Positions per account | Accounts | Positions |
|---|---|---|---|
| Small | 400 | 1,920 | 768,000 |
| Medium | 1,000 | 3,840 | 3,840,000 |
| Large | 2,500 | 4,680 | 11,700,000 |
| **Total** | | **10,440** | **16,308,000** |

Storage: 16.3M × 300 bytes = **4.9 GB** for one copy.

### The skew

| | Share of clients | Share of positions |
|---|---|---|
| Large | 10% | **72%** |

40 clients hold 11.7 million of the 16.3 million positions.

That single fact explains almost every problem in the old system. The grid hung "on the biggest funds." End-of-day ran late because of a few accounts. One client could starve everyone.

If you test with evenly-spread data, **you will never see any of it.** The generator has to produce this skew or the whole exercise is fake.

### Where the laptop stops

| | Size |
|---|---|
| One copy of positions | 4.9 GB |
| Kafka + Postgres + Flink state together | ~34 GB |
| Free disk available | **17 GB** |

---

## Number 5 — 1,050,030 products

A product is the thing a position points at. Example: *Airbus stock*.

Products are **shared**. Many funds hold Airbus. One product row, thousands of positions pointing at it.

| Type | Distinct count | Shared between clients? |
|---|---|---|
| Equities, bonds, futures | 250,000 | Yes — everyone holds the big names |
| FX forwards & swaps | 800,000 | No — each contract belongs to one fund |
| Cash & accrual | 30 | Yes — one per currency |
| **Total** | **1,050,030** | |

Storage: 1.05M × 400 bytes = **420 MB**.

### The most important number in the project

Of the 16.3M positions, about 9.8M are securities. They point at 250,000 instruments.

```
9,800,000 positions ÷ 250,000 instruments = 39
```

**When one price arrives, 39 positions need recalculating.** That is small. A price tick is cheap.

FX rates are different. There are only 30 currencies, and *every* position has a currency:

```
16,300,000 positions ÷ 30 currencies = 543,000
```

**When one FX rate moves, 543,000 positions are affected.**

Same system. Same kind of event. **14,000 times more work.**

That gap is the hardest problem in this system, and it is why the exposure engine cannot treat both the same way.

---

## Number 6 — 30 currencies

Every position has a currency. Every fund reports in one base currency.

| Group | Count | Examples |
|---|---|---|
| Majors | 8 | USD, EUR, GBP, JPY, CHF, CAD, AUD, NZD |
| Developed | 8 | SEK, NOK, DKK, HKD, SGD, ILS, PLN, CZK |
| Emerging | 13 | CNH, KRW, TWD, INR, BRL, MXN, ZAR, TRY, THB, IDR, PHP, HUF, RUB |
| Metal | 1 | XAU |
| **Total** | **30** | |

Storage: 30 × 100 bytes = **3 KB**.

Three things this tiny number causes:

1. **CNH is not a real ISO currency.** It is offshore yuan. `Currency.getInstance("CNH")` throws. Handled in `Ccy.java` via the `NON_ISO` map.
2. **JPY has no decimals.** ¥100 is whole. USD has 2. KWD has 3. Round wrong and the numbers are wrong.
3. **30 currencies is why FX fan-out hurts.** Small denominators create big fan-out. If there were 250,000 currencies it would be a non-problem.

### FX pairs

Funds report in 8 base currencies. Positions sit in 30.

```
8 base × 30 position currencies = 240 pairs
```

240 rates, updating all day.

---

## Number 7 — arrival rates

Where the 16.3M positions come from:

| Source | Positions |
|---|---|
| Internal feed | 9,276,000 |
| External custodians | 7,032,000 |
| **Total** | **16,308,000** |

External splits across its three paths:

| Path | Positions |
|---|---|
| SFTP overnight | 4,922,000 |
| SFTP intraday | 1,406,000 |
| UI upload | 704,000 |

### Speeds

| Stream | Rate | When |
|---|---|---|
| Internal end-of-day load | **7,733/sec** | 20-min window, nightly |
| SFTP overnight | 228/sec | spread over 6 hours |
| SFTP intraday | 49/sec average, bursty | any time |
| UI upload | one file at a time, 5k–50k rows | market hours |
| Price ticks | 208/sec average, **4,167/sec burst** | every 20 min |
| FX rates | 100/sec, 500/sec burst | all day |
| Trades | 8/sec average, 82/sec peak | open and close |

**The peak is 7,733 positions per second**, during the nightly internal load. That is the number the ingestion path must survive.

### Trades by client size

| Client size | Trades per fund/day | Funds | Trades |
|---|---|---|---|
| Small | 20 | 480 | 9,600 |
| Medium | 100 | 480 | 48,000 |
| Large | 500 | 360 | 180,000 |
| **Total** | | **1,320** | **237,600** |

Large clients make 76% of the trades. Same skew again.

---

## Number 8 — revaluations per second

Two things trigger recalculation. They cost wildly different amounts.

### Price ticks — cheap

```
208 ticks/sec × 39 positions each = 8,112 revaluations/sec
4,167 burst   × 39                = 162,513 revaluations/sec
```

Eight cores handle that.

### FX rates — impossible, done the obvious way

```
100 FX updates/sec × 543,000 positions each = 54,300,000 revaluations/sec
```

54 million per second. No machine does this. Not a cluster either.

### The fix — keep the total, multiply the total

Exposure is a **sum**. Fund A's EUR exposure is just "add up every EUR position in Fund A." When the EUR rate moves, the sum does not need rebuilding — you multiply the sum you already have.

**Worked example.** Fund A holds Airbus 100 EUR, SAP 200 EUR, BMW 300 EUR. Total 600 EUR. Rate moves from 1.10 to 1.05.

Slow way — touch every position:

```
100 × 1.05 = 105
200 × 1.05 = 210
300 × 1.05 = 315
            -----
            630 USD          ← 3 calculations
```

Fast way — we already stored *Fund A EUR total = 600*:

```
600 × 1.05 = 630 USD         ← 1 calculation
```

Same answer. With 543,000 positions in EUR, the slow way costs 543,000 calculations. The fast way still costs 1.

### Why prices cannot use the same trick

Airbus rises 10% to 110 EUR. The 600 total cannot simply be multiplied — SAP and BMW did not move. So use the **difference** instead:

```
Airbus was 100, now 110  →  difference = +10
New total = 600 + 10 = 610 EUR
```

One position touched, not three. An Airbus price update only matters to positions holding Airbus — **39 of 16.3 million**.

### The two rules

| Event | What we do | Work |
|---|---|---|
| FX rate moves | Multiply the fund's currency total | 1 |
| Price moves | Find the few holders, add the difference | 39 |

Neither ever touches 16.3 million positions. The old system did. That is why it hung.

### Aggregate state

```
1,320 funds × 30 currencies = 39,600 aggregates
```

At 100 FX updates/sec, one EUR move touches at most 1,320 aggregates — one per fund:

```
100 × 1,320 = 132,000 aggregate updates/sec
```

| Approach | Work per FX move |
|---|---|
| Revalue every position | 543,000 |
| Update the aggregate | 1,320 |
| **Difference** | **411× less** |

---

## Decision — compute for all clients, not just active ones

Only ~5% of clients are logged in at any moment. Tempting to calculate only for them. **We do not.**

Three things need the numbers even when nobody is watching:

| Need | Why it cannot wait |
|---|---|
| Forward maturity alerts | Contract matures in 3 days, client on holiday. Alert must still fire. |
| End-of-day reports | Go to all 400 clients nightly, logged in or not. |
| Page load speed | Client logs in at 9am. Nothing computed means rebuilding their whole book while they wait. |

And after the aggregate trick it costs almost nothing:

```
39,600 aggregates × 200 bytes = 8 MB
```

8 MB of memory, for all 400 clients, always warm.

| | FX updates/sec |
|---|---|
| All 400 clients | 132,000 |
| Only 20 viewing clients | 6,600 |

Laziness would save 95% of a number that was already comfortable, while breaking alerts and slowing every login.

**Where laziness does belong:** the big grid tree with drill-downs. Expensive, and only matters when someone is looking. Build on demand, discard after.

---

## Market opens — the real concurrency peak

There are four market opens, not one:

| Open | UTC | Who logs in |
|---|---|---|
| Tokyo | 00:00 | Asia clients |
| Hong Kong | 01:30 | Asia clients |
| London | 08:00 | Europe clients |
| **New York** | **14:30** | **US clients — the biggest** |

### Client base by region

| Region | Clients | Users (5 each) |
|---|---|---|
| US | 200 | 1,000 |
| Europe | 120 | 600 |
| Asia | 80 | 400 |
| **Total** | **400** | **2,000** |

### Concurrent users at New York open

```
US users active:      1,000 × 60% = 600
Europe still working:   600 × 30% = 180
                                  -----
                                    780
```

**Peak concurrency is 780, not the 200 stated in `scale-targets.md`. That earlier figure was wrong.**

### Everything lands at once

| At 14:30 UTC | Load |
|---|---|
| Users logging in | 600 over ~15 minutes |
| Each login | loads their whole grid |
| Price ticks | 4,167/sec burst |
| Trades starting | ramping to 82/sec |
| Intraday custodian files | arriving too |

This is precisely when the old system died — OutOfMemory and connection pool exhaustion. Not a coincidence.

### Two things save us

1. **The opens are hours apart.** Four spikes of 780, 460 and 240 — not one spike of 2,000. Geography helps.
2. **This is why we compute eagerly.** Lazy computation means 600 simultaneous cold-start rebuilds at 14:30. The aggregates being warm turns a login into a read.

The two decisions connect: **laziness looks cheap until market open, then it kills you.**

---

## Number 9 — memory and state

State is what the system holds in memory to avoid recalculating.

| What | How many | Size each | Total |
|---|---|---|---|
| Position values | 16,308,000 | 300 bytes | **4.9 GB** |
| Product reference data | 1,050,030 | 400 bytes | 420 MB |
| Exposure aggregates | 39,600 | 200 bytes | 8 MB |
| FX rates | 240 | 100 bytes | 24 KB |
| **Total** | | | **~5.3 GB** |

Flink stores this in RocksDB, which spills to disk but keeps indexes and caches in memory — roughly **3× the raw size**.

```
5.3 GB × 3 = ~16 GB of Flink state
```

Against the dev laptop: 16 GB needed, 11 GB of Docker RAM available. Does not fit, before Kafka, Postgres and Redis are even counted.

### What is cheap and what is expensive

| | Size | Why |
|---|---|---|
| Exposure aggregates | 8 MB | the thing queried constantly |
| Position values | 4.9 GB | the thing almost never read directly |

The 8 MB answers every user question. The 4.9 GB just feeds it. A hint that positions may not all need to sit in fast state.

---

## Number 10 — disk

| What | Size | Why |
|---|---|---|
| Kafka — 7 days of events | **34 GB** | every position message kept for replay |
| Postgres — current positions | 12 GB | 16.3M rows plus indexes |
| Flink — RocksDB state files | 16 GB | number 9, on disk |
| Docker images | 2 GB | Kafka, Flink, Postgres, Redis |
| **Total** | **~64 GB** | |

How Kafka reaches 34 GB:

```
16.3M positions × 300 bytes = 4.9 GB per daily snapshot
4.9 GB × 7 days             = 34 GB
```

That is one copy. Production keeps three: **102 GB**.

### The seven-year archive

```
16.3M positions × 250 trading days × 7 years = 28,500,000,000 rows
```

**28.5 billion rows**, roughly **5.7 TB** compressed. This never lives on a laptop or in Kafka — it goes to object storage, written once and read rarely.

---

## Number 11 — CPU

Work per second at peak:

| Job | Operations/sec |
|---|---|
| Position ingest, EOD peak | 7,733 |
| Price revaluations, burst | 162,513 |
| FX aggregate updates | 132,000 |
| Grid queries | ~200 QPS across 780 users |

One core does roughly **50,000 simple state operations/sec** with RocksDB — read, decimal multiply, write.

```
162,513 + 132,000 = 294,513 ops/sec at peak
294,513 ÷ 50,000  = 6 cores for calculation alone
```

| Component | Cores |
|---|---|
| Flink calculation | 6 |
| Kafka broker | 2 |
| Postgres | 2 |
| API and gateway | 2 |
| OS and headroom | 2 |
| **Total** | **14** |

The dev laptop has 8. An `r6i.2xlarge` has 8 — tight but workable, since peaks do not all land in the same second. `r6i.4xlarge` (16 cores) is worth it only for final measured runs.

---

## Number 12 — latency targets

| Path | Target | Why that number |
|---|---|---|
| API gateway overhead | **20 ms** | invisible to a human |
| Grid query | **300 ms** | feels instant |
| Trade → visible in exposure | **2 sec** | trader clicks, glances back, it is there |
| Price tick → new market value | **5 sec** | prices are 20 min delayed anyway |
| Hedge calculation on request | **1 sec** | user pressed a button and is waiting |
| Custodian file validated | **2 sec** per 50k rows | user watching an upload spinner |

### Why p99, never average

100 users load the grid. 99 take 200 ms, one takes 8 seconds. Average is 278 ms — looks excellent. But one user per hundred believes the system is broken.

At 780 concurrent users that is **8 angry people per refresh**.

So the measure is **p99**: the slowest 1%. A p99 of 300 ms means 99 of every 100 users got an answer inside 300 ms.

### Where the 300 ms goes

```
Gateway (auth, rate limit)      20 ms
Query service                   30 ms
Read aggregates from memory     50 ms
Build the tree                 150 ms
Network back to browser         50 ms
                               ------
                               300 ms
```

No single step is allowed to be slow.

---

## Number 13 — Kafka partitions

A partition is one lane. Kafka's rule: **one partition is read by one consumer at a time.** Partitions set the parallelism ceiling.

Position messages are keyed by account, so an account's messages always land in the same partition and stay in order.

```
10,440 accounts ÷ 128 partitions = 82 accounts per partition
```

### Why 128

| Partitions | Accounts each | Problem |
|---|---|---|
| 16 | 653 | only 16 workers, too slow |
| 128 | 82 | good |
| 2,000 | 5 | Kafka slows — each partition costs memory and file handles |

More is not free. Every partition is real files and open handles on the broker.

### The hot-partition trap

Large clients hold 4,680 of the 10,440 accounts, at 2,500 positions each.

If accounts land by hash alone, one partition might get 82 small accounts (33,000 positions) while another gets 82 large ones (205,000 positions).

**One lane does 6× the work of another.** That lane becomes the bottleneck and the other 127 wait. This is a **hot partition**, and it is exactly what broke the old system.

**The fix:** key on account, but assign accounts to partitions deliberately so large and small are mixed evenly — not by hash luck. Build it wrong first, observe the skew, then fix it.

---

## Number 14 — Flink task slots

A slot is one worker running one piece of one job.

**Rule: slots should divide evenly into partitions.**

```
128 partitions ÷ 32 slots = 4 partitions per slot
```

With 30 slots it would be 4.27 — some slots take 5, finish last, and everyone waits.

### Arrangement

| | |
|---|---|
| TaskManagers | 8 |
| Slots per TaskManager | 4 |
| **Total slots** | **32** |
| RAM per TaskManager | 4 GB |
| **Total RAM** | **32 GB** |

### Why 4 slots per machine, not 32 on one

| Setup | When one dies |
|---|---|
| 1 machine × 32 slots | everything stops |
| 8 machines × 4 slots | lose 1/8, the rest continue |

Four slots sharing 4 GB gives each about 1 GB — enough for RocksDB caches without them fighting.

### Slots per job

| Job | Slots | Why |
|---|---|---|
| Valuation | 32 | the heavy one, 16.3M positions |
| Position loader | 32 | must keep up with 7,733/sec |
| Exposure | 16 | only 39,600 aggregates |
| Hedge calc | 8 | small |
| Forward maturity | 8 | timers, mostly idle |
| Fill processor | 8 | 82 trades/sec peak |

Slots are shared between jobs, so the total is not 104 — idle jobs release capacity.

---

## Number 15 — recovery time

Flink writes a checkpoint every 60 seconds — a snapshot of all state, saved to disk.

When a worker dies:

```
Detect the failure             10 sec
Restart the worker             15 sec
Reload state from disk         20 sec
Replay Kafka since checkpoint  15 sec
                              -------
                               60 sec
```

Nothing is lost. Kafka still holds every message, so processing replays from where the checkpoint stopped.

### RPO and RTO

| Term | Means | Ours |
|---|---|---|
| **RPO** | how much data is lost | **0** — Kafka has it all |
| **RTO** | how long you are down | **60 sec** |

### Why 60 seconds between checkpoints

| Interval | Replay after crash | Cost while running |
|---|---|---|
| 10 sec | 10 sec | constant disk writes, slows everything |
| 60 sec | up to 60 sec | small |
| 10 min | up to 10 min | almost none |

Checkpointing is **incremental** — it writes what changed, not all 16 GB. That is why it completes in under 10 seconds.

### Against the old system

| | Old | New |
|---|---|---|
| Recovery | rerun the batch, **hours** | **60 sec** |
| Data lost | whatever the batch had not written | none |

---

## All 15 numbers

| # | Number | Value |
|---|---|---|
| 1 | Clients | 400 |
| 2 | Funds | 1,320 |
| 3 | Accounts | 10,440 (6,360 internal, 4,080 external) |
| 4 | Positions | 16,308,000 |
| 5 | Products | 1,050,030 |
| 6 | Currencies | 30, and 240 FX pairs |
| 7 | Peak ingest | 7,733 positions/sec |
| 8 | Peak revaluations | 294,513/sec |
| 9 | Flink state | ~16 GB |
| 10 | Disk | ~64 GB live, 5.7 TB archive |
| 11 | CPU | 14 cores |
| 12 | Grid latency | 300 ms p99 |
| 13 | Kafka partitions | 128 |
| 14 | Flink slots | 32 |
| 15 | Recovery | 60 sec, RPO 0 |

Plus: **780 concurrent users** at New York market open.

---

*Next: Step 5 — Kafka topics and the position generator.*
