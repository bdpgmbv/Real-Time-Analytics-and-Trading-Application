# Senior / Architect Interview Questions from the rtat Build

Questions raised naturally by decisions made while building. Each one has come up in real senior and staff interviews. Appended as we go.

---

## 1 — Eager vs lazy state for multi-tenant systems

> You have 400 tenants but only 5% are active at any moment. Do you maintain computed state for all of them, or compute on demand when a user arrives? Defend your choice.

**What they are testing:** whether you reason about *all* consumers of the state, not just the UI.

**Strong answer.** It depends what else reads the state. If only the UI reads it, be lazy. If alerts, scheduled reports, or a cold-start SLA also read it, be eager. Then cost both.

In rtat: aggregating exposure to `(fund, currency)` reduced state to 39,600 keys, about 8 MB. Eager for all 400 tenants costs almost nothing, and laziness would break maturity alerts, nightly reporting, and login latency.

**Weak answer:** "Lazy, to save resources" — without asking what else depends on it.

---

## 2 — Kafka reachable inside Docker but not outside

> Kafka is running in Docker. It works from other containers, but your application cannot connect. Walk me through why.

**What they are testing:** whether you know that a Kafka client is redirected after its first connection.

**Strong answer.** Kafka does not simply accept the connection you make. On connect it returns metadata telling the client which address to use for the actual brokers — `advertised.listeners`, not `listeners`. If that value is the Docker service name, only containers on that network can resolve it. A client on the host fails with `UnknownHostException`, even though the port is published correctly.

The fix is two listeners on different ports, each advertising an address valid for its caller:

```
KAFKA_LISTENERS: INTERNAL://:29092,EXTERNAL://:9092,CONTROLLER://:9093
KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:29092,EXTERNAL://localhost:9092
```

**Weak answer:** "the port isn't mapped." The port is mapped — that is what makes this confusing. The redirect is the mechanism, and candidates who have not hit it in practice reach for port mapping first.

*Observed live during rtat Step 1: identical command succeeded inside the network and failed from the host with `java.net.UnknownHostException: kafka`.*

---

## 3 — Provisioning for periodic traffic peaks

> Your system has four traffic peaks a day, six hours apart, driven by market opens. Do you autoscale or provision for peak? What breaks if you get it wrong?

**What they are testing:** whether you understand autoscaling lag versus spike shape.

**Strong answer.** Autoscaling reacts in minutes; a login storm arrives in seconds. Provision the stateful tier (Kafka, Flink, database connections) for peak, autoscale only the stateless tier. Note that Flink rescaling requires a savepoint and restart — it cannot absorb a spike in real time.

Mention the saving grace in rtat: the four opens are hours apart, so the peak is 780 concurrent users rather than 2,000.

**Weak answer:** "Autoscale" — without noting that scaling stateful streaming components mid-spike is not viable.

---

## 4 — Fan-out asymmetry in event-driven systems

> Two event types hit the same system. One affects 39 records, the other affects 543,000. Same code path?

**What they are testing:** whether you spot that uniform handling of non-uniform events is the bug.

**Strong answer.** No. The cheap event recomputes the affected records directly. The expensive event must be restructured so the work does not scale with the record count — in rtat, exposure is a sum, so an FX rate move multiplies the stored aggregate instead of revaluing every position underneath it. 543,000 operations becomes 1.

Mention the general principle: when fan-out is large, look for an algebraic property — associativity, distributivity — that lets you operate on the aggregate rather than the parts.

**Weak answer:** "Scale out the cluster." The naive path needs 54 million operations per second. No cluster fixes that.

---
