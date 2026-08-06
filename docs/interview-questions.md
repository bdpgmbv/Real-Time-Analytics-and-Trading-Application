# Senior / Architect Interview Questions from the rtat Build

Questions raised by design decisions in the exposure and hedging system itself — the parts an interviewer would actually probe.

Deliberately excluded: build tooling, Docker configuration, and the data-sourcing scaffolding. Those are real problems and they get solved, but nobody is asked about a Kafka listener setting or an HTTP header in a senior design interview. Keeping them out means every question here is worth rehearsing.

---

## 1 — Eager vs lazy state for multi-tenant systems

> You have 400 tenants but only 5% are active at any moment. Do you maintain computed state for all of them, or compute on demand when a user arrives? Defend your choice.

**What they are testing:** whether you reason about *all* consumers of the state, not just the UI.

**Strong answer.** It depends what else reads the state. If only the UI reads it, be lazy. If alerts, scheduled reports, or a cold-start SLA also read it, be eager. Then cost both.

In rtat: aggregating exposure to `(fund, currency)` reduced state to 39,600 keys, about 8 MB. Eager for all 400 tenants costs almost nothing, and laziness would break maturity alerts, nightly reporting, and login latency.

**Weak answer:** "Lazy, to save resources" — without asking what else depends on it.

---

## 2 — Provisioning for periodic traffic peaks

> Your system has four traffic peaks a day, six hours apart, driven by market opens. Do you autoscale or provision for peak? What breaks if you get it wrong?

**What they are testing:** whether you understand autoscaling lag versus spike shape.

**Strong answer.** Autoscaling reacts in minutes; a login storm arrives in seconds. Provision the stateful tier (Kafka, Flink, database connections) for peak, autoscale only the stateless tier. Note that Flink rescaling requires a savepoint and restart — it cannot absorb a spike in real time.

Mention the saving grace in rtat: the four opens are hours apart, so the peak is 780 concurrent users rather than 2,000.

**Weak answer:** "Autoscale" — without noting that scaling stateful streaming components mid-spike is not viable.

---

## 3 — Fan-out asymmetry in event-driven systems

> Two event types hit the same system. One affects 39 records, the other affects 543,000. Same code path?

**What they are testing:** whether you spot that uniform handling of non-uniform events is the bug.

**Strong answer.** No. The cheap event recomputes the affected records directly. The expensive event must be restructured so the work does not scale with the record count — in rtat, exposure is a sum, so an FX rate move multiplies the stored aggregate instead of revaluing every position underneath it. 543,000 operations becomes 1.

Mention the general principle: when fan-out is large, look for an algebraic property — associativity, distributivity — that lets you operate on the aggregate rather than the parts.

**Weak answer:** "Scale out the cluster." The naive path needs 54 million operations per second. No cluster fixes that.

---
