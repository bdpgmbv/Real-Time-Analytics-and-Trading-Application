# Data Model — 15 Tables

Every table below was argued for one at a time against the product description and
the screenshots of the real system. The list started at 33 and ended at 15.

Quantities live in [scale-numbers.md](scale-numbers.md). This document is about **shape**.

---

## The six features everything serves

From the product description:

| Feature | Tables |
|---|---|
| Automated feed or manual upload of transactions | `trade` |
| Pricing, NAV and PnL | `price`, computed |
| Customise the exposure of a security, generic or specific | **`position_exposure`** |
| FX exposure calculations | computed from `position` |
| Auto-calculated hedges, client can customise | `hedge` |
| Send button, entitled users only | `hedge`, `app_user`, `entitlement` |

---

# Reference data

## 1. `currency` — 30 rows

| code | name | minor_units |
|---|---|---|
| USD | US Dollar | 2 |
| EUR | Euro | 2 |
| JPY | Japanese Yen | 0 |
| KRW | South Korean Won | 0 |
| CNH | Offshore Yuan | 2 |
| XAU | Gold | 4 |

**Why a table:** stops a position being written in currency `"XYZ"` or `"eur"`.

**Why `minor_units`:** the grid displays 2 decimals for everything, so it is not used for display.
It matters at one moment — sending a hedge to market. A forward in fractional yen is rejected.

**Dropped:** `ccy_group` (a label nothing reads), `is_iso` (that Java throws on CNH is a code
problem, handled in code).

## 2. `exchange` — ~70 rows

| mic | name |
|---|---|
| XLON | London Stock Exchange |
| XETR | Xetra |
| XNYS | New York Stock Exchange |
| XASX | Australian Securities Exchange |

**Honest reason it exists:** it is a display column in the Security Exposure grid, nothing more.
No calculation reads it. `issue_ccy` and `md_symbol` already distinguish the same company on two
venues — the exchange adds no information either of those didn't carry.

It is a table rather than text on `product` only so the name isn't stored a million times and
misspelt in some of them.

## 3. `fx_rate` — 240 rows/day

| from_ccy | to_ccy | rate | as_of | source |
|---|---|---|---|---|
| HKD | USD | 0.1282410000 | 2026-08-06 | ECB_CROSS |
| GBP | USD | 1.2759000000 | 2026-08-06 | ECB_CROSS |
| JPY | USD | 0.0068170000 | 2026-08-06 | ECB_CROSS |
| USD | USD | 1.0000000000 | 2026-08-06 | ECB_CROSS |
| EUR | USD | 1.1542000000 | 2026-08-06 | ECB |
| TWD | USD | 0.0312000000 | 2026-08-06 | DERIVED |

**Read as:** 1 `from_ccy` = `rate` × `to_ccy`.

**Why not `base`/`quote`:** `fund.base_ccy` already means something completely different — the
currency a fund reports in. Two meanings for one word is how bugs get written.

**`USD → USD = 1.0` is stored**, not special-cased. The grid shows it as a real row, and it removes
an `if` from the code.

**`source` matters:** the ECB publishes 26 of the 30 currencies. CNH, TWD, RUB and XAU are invented.
You must always be able to tell which is which.

**One row per pair per day.** Live ticks belong in Kafka; a database is the wrong place to write
several thousand rows a second of reference data.

## 4. `product` — 1,050,030 rows

| product_id | product_type | name | issue_ccy | settle_ccy | exchange_mic | md_symbol | cusip | maturity | contract_ref | is_real |
|---|---|---|---|---|---|---|---|---|---|---|
| 100101 | SECURITIES | ABBVIE INC | USD | USD | XNYS | abbv.n | 00287Y109 | | | true |
| 100102 | SECURITIES | AGILENT TECHNOLOGIES INC | USD | USD | XNYS | a.n | 00846U101 | | | true |
| 100103 | EQUITY SWAP | AGILENT TECHNOLOGIES INC | USD | USD | XNYS | a.n | 00846U101 | | | true |
| 100104 | SECURITIES | AIG CB 0 09NOV2031 | USD | USD | | | 026874AP2 | 2031-11-09 | | true |
| 900001 | CASH | THE EURO | EUR | EUR | | EUR.CASH | 999850126 | | | false |
| 900002 | ACCRUAL | THE EURO | EUR | EUR | | EUR.CASH | 999850126 | | | false |
| 700221 | CURRENCY SPOT | HKD TO USD SPOT SD 12-Aug-2026 | HKD | HKD | | | HKUS4225! | 2026-08-12 | SPOT-4225 | false |
| 700222 | CURRENCY SPOT | USD TO HKD SPOT SD 12-Aug-2026 | USD | USD | | | USHK4225! | 2026-08-12 | SPOT-4225 | false |

**CUSIP is not unique.** Two cases, both visible in the real system:

- `CASH` and `ACCRUAL` share `999850126`
- one name held as both `SECURITIES` and `EQUITY SWAP` shares one CUSIP

So the unique key is `(cusip, product_type)`.

**Forwards are products, not a separate table.** The real system stores each leg as its own row
with its own synthetic CUSIP — `CHEU4262$` and `EUCH4262$`, the prefix flipped, the suffix shared.
`contract_ref` links them.

**Why `product_id` exists at all:** the natural key repeats on 16.3 million position rows.
`(cusip, product_type)` is ~20 bytes; an integer is 4. That is 260 MB saved on `position` alone.

**Dropped:** `coupon` — prices are taken as given, accruals are not computed here.

---

# Who owns what

## 5. `client` — 400 rows

| client_id | name | tier | region |
|---|---|---|---|
| 1 | Helikon Investments Limited | LARGE | EUROPE |
| 2 | Brevan Ridge Capital | LARGE | US |
| 3 | Katsura Asset Management | MEDIUM | ASIA |
| 4 | Cedar Point Partners | SMALL | US |

**`tier` and `region` are read by no calculation.** They shape the generated data:

- `tier` → 40 large clients end up holding 72% of all positions
- `region` → 780 users arrive at New York open, not 2,000 at once

Without them the data spreads evenly and every performance test passes for the wrong reason.

## 6. `fund` — 1,320 rows

| fund_id | client_id | name | base_ccy |
|---|---|---|---|
| 12 | 1 | Demo Offshore Fund | USD |
| 13 | 1 | Helikon Long Short | EUR |
| 15 | 3 | Katsura Japan Equity | JPY |

**`base_ccy` is why this cannot merge into `client`.** One client runs several funds and they do
not all report in the same currency. Every `(Base)` column converts into this.

**Dropped:** `hedge_instrument` and `hedge_ratio`. The instrument is chosen per hedge, not per fund.
And the ratio was an invention — in the real grid, intended exposure equals full exposure, so no
ratio is being applied.

## 7. `account` — 10,440 rows

| account_id | fund_id | name | custodian |
|---|---|---|---|
| 340 | 12 | CBXXX | |
| 341 | 12 | PB-GS-001 | Goldman Sachs |
| 342 | 12 | PB-JPM-004 | JP Morgan |

**`custodian` null means internal** — our own accounting system. Set means an outside bank.
6,360 internal, 4,080 external.

**Dropped:** `kind` (derivable from `custodian`) and `arrival_path`. The second was wrong outright —
a client can send an SFTP file *and* upload a correction from the UI for the same account. How data
arrives describes an event, not a holding.

---

# Holdings

## 8. `position` — 16,308,000 rows

| account_id | product_id | quantity | cost_basis | purpose | price_override | comments | as_of |
|---|---|---|---|---|---|---|---|
| 340 | 100101 | -42786.0000 | -8100245.52 | SPECULATIVE | | | 2026-08-06 |
| 340 | 100102 | -100420.0000 | -14857876.50 | SPECULATIVE | | | 2026-08-06 |
| 340 | 700221 | 183280.0000 | 23200.00 | HEDGE | | | 2026-08-06 |
| 340 | 100201 | 12269.0000 | 63187.44 | 4.040000 | vendor price stale | 2026-08-06 |

**Key is `(account_id, product_id)`.** No surrogate id. A custodian re-sending the same file
overwrites in place instead of doubling the book — and your doc says re-sends are guaranteed.

**Snapshot only.** History lives in `trade`, which is append-only. The seven-year position history
is 28.5 billion rows and belongs in object storage.

**`quantity` is signed.** Negative is a short. No separate `side` column that could contradict it.

**`purpose` cannot be inferred from the product type.** A macro fund holds forwards to bet on a
currency; an equity fund holds them to kill exposure. Same instrument, opposite meaning. Get it
wrong and the system hedges its own hedge, forever.

**Two columns moved to `product`:** `issue_ccy`, `settle_ccy` — they describe the instrument, and
they sit in the *Security* grid in the real UI.

**One column dropped as derivable:** `quantity_change`. It is today's trades summed:

```sql
SELECT SUM(quantity) FROM trade
WHERE account_id = ? AND product_id = ? AND trade_date = current_date
```

Storing it means a column on 16.3M rows that must be reset nightly and can drift from the trades
it claims to summarise.

## 9. `position_exposure` — sparse

| account_id | product_id | seq | ccy | weight |
|---|---|---|---|---|
| 340 | 100102 | 0 | USD | 70.00 |
| 340 | 100102 | 1 | CHF | 30.00 |
| 340 | 100102 | 6 | JPY | 15.00 |
| 341 | 100102 | 1 | CHF | 50.00 |

**This is the table that makes it FX Analyzer rather than a generic portfolio system.**

From the description: *"Ability to customize the exposure of a security to a specific currency.
The exposure can be 'generic' or 'specific'."*

```
seq 0    the generic exposure, written ONLY if a user edits it
seq 1+   the numbered Specific Exposure slots from the grid
```

**Nothing is stored for an untouched position.** The default is `product.issue_ccy` at 100% —
which is what the real grid shows on every row: generic CCY always equals issue CCY, weight always
100.00. That is a rule, not data, and deriving it saved 16 million rows.

**Specific exposures add, they do not replace.** The description says *"**secondary** exposures to
**other** currencies"*. So a EUR stock with 30% CHF specific is EUR 100% **plus** CHF 30%.

**Per position, not per security.** The same security in two accounts can carry different weights —
confirmed against the real system. A position *is* `(account_id, product_id)`, so the key already
expresses this.

**Unique on `(account_id, product_id, ccy)`** stops the same currency landing in two slots and
silently doubling.

## 10. `price` — 250,030 rows/day

| product_id | price | timing | as_of | received_at |
|---|---|---|---|---|
| 100101 | 189.320000 | DELAYED | 2026-08-06 | 2026-08-06 14:10:00 |
| 100104 | 7.400000 | PRIOR_EOD | 2026-08-05 | 2026-08-05 22:00:00 |
| 100105 | 327.000000 | EOD | 2026-08-05 | 2026-08-05 21:00:00 |
| 100301 | 0.000000 | COST | 2026-08-06 | 2026-08-06 09:00:00 |
| 700221 | 1.000000 | FX_FORWARD | 2026-08-06 | 2026-08-06 14:00:00 |

**`timing` is the pricing-staleness problem, visible on screen as a word.** All five values are
taken from the real grid. `COST` means no market price exists at all.

**`received_at` as well as `as_of`:** staleness is `now() - received_at`. A `PRIOR_EOD` price is
`as_of` yesterday; a `DELAYED` price is `as_of` today but 20 minutes old. `as_of` alone cannot
tell you.

**Dropped:** `ccy` — a price is always in `product.issue_ccy`.

**One row per product per day.** Ticks go to Kafka.

---

# Activity

## 11. `trade` — 237,600 rows/day

| trade_id | account_id | product_id | quantity | price | executed_at | trade_date | entry_method |
|---|---|---|---|---|---|---|---|
| 9001 | 340 | 100101 | 100.0000 | 189.320000 | 2026-08-06 14:32:07 | 2026-08-06 | AUTOMATED_FEED |
| 9002 | 341 | 100102 | -25.0000 | 137.040000 | 2026-08-06 15:01:44 | 2026-08-06 | MANUAL_UPLOAD |

**Append-only.** This is where "every entry is kept" actually happens. `position` holds the answer;
`trade` holds the history.

**A position statement is not a trade.** A custodian file saying "you hold 30" replaces "you hold
20" — it does not add to it. That is why the position key is natural, not surrogate.

**`executed_at` and `trade_date` are both kept.** A trade at 23:40 UTC is already tomorrow in Tokyo.
The business date is a decision, not a timezone conversion.

**Dropped:** `side` (signed quantity says it), `settle_date` (on `product.maturity` for forwards,
unused for equities), `ccy` (on the product).

---

# Hedging

## 12. `hedge` — ~2,000 rows/day

| hedge_id | fund_id | ccy | as_of | exposure_amount | suggested_amount | final_amount | instrument | settle_date | status | sent_by | sent_at | external_ref |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 502 | 12 | HKD | 2026-08-06 | 245041885.43 | -245041885.43 | -240000000.00 | FORWARD | 2026-09-18 | CUSTOMIZED | | | |
| 503 | 12 | JPY | 2026-08-06 | 3676923340.00 | -3676923340.00 | -3676923340.00 | SPOT | 2026-08-12 | FILLED | u_1042 | 2026-08-06 14:32:07 | FXM-77120 |

**Per fund, not per account.** Exposure is per position; hedging is per fund. The description is
explicit: *"a fund will aggregate all its currency position into one trade."* You net the whole
fund's EUR exposure and send one EUR trade.

**A row exists only once a human acts.** An untouched suggestion is computed and displayed, never
written. What must be stored is what cannot be recomputed: the client's edited number, and the fact
that an order went to the outside world.

**`suggested_amount` and `final_amount` are both kept**, so you can always answer *"did the client
override us, and by how much?"* — the first question asked when a hedge goes wrong.

**Recommendation and instruction are one table, not two.** They are one thing at two stages, which
is what `status` is for.

## 13. `hedge_fill` — ~2,600 rows/day

| fill_id | hedge_id | filled_amount | rate | filled_at | external_ref |
|---|---|---|---|---|---|
| 7701 | 503 | 6000000.00 | 1.1540000000 | 2026-08-06 14:32:11 | FXM-77120-1 |
| 7702 | 503 | 3000000.00 | 1.1538000000 | 2026-08-06 14:32:14 | FXM-77120-2 |

**One order fills in several pieces at several rates.** A single `filled_rate` column on `hedge`
would force a weighted average and throw the legs away — and the difference between the quoted
rate and the achieved rate is real money.

Filled total and weighted-average rate are derived:

```sql
SELECT SUM(filled_amount),
       SUM(filled_amount * rate) / SUM(filled_amount),
       MAX(filled_at)
FROM hedge_fill WHERE hedge_id = ?
```

**`hedge.status` still earns its place:** `SENT` and `CANCELLED` both have zero fills, so the fills
alone cannot tell them apart.

---

# Access

## 14. `app_user` — 2,000 rows

| user_id | client_id | name | email |
|---|---|---|---|
| u_1042 | 1 | A. Fischer | a.fischer@example.com |
| u_1043 | 1 | R. Baumann | r.baumann@example.com |
| u_2001 | | Internal Ops 1 | ops1@example.com |

**`client_id` is nullable** because the real system has internal fund services staff who belong to
no single client — the footer shows an "Aliasing as" control, and the client dropdown includes an
internal team. That feature is out of scope, but the column has to allow those users to exist.

**Needed for two concrete things:** `entitlement.user_id`, and `hedge.sent_by` — a trade went to
market, and the record has to name who sent it.

## 15. `entitlement` — ~10,000 rows

| user_id | fund_id | can_send |
|---|---|---|
| u_1042 | 12 | true |
| u_1042 | 13 | true |
| u_1043 | 12 | false |

**The only thing in the schema stopping one client from seeing another's book.**

**No `can_view` column.** A row existing *is* permission to view; the column would be `true` on
every row. `can_send` is the one real permission — the right to put a trade into the market.

**Fund level, not account level.** A user entitled to a fund sees all its accounts.

---

# What is deliberately not stored

| Not a table | Why |
|---|---|
| `position_value` (MV, NAV, G/L per position) | derivable. Storing it means 54,300,000 row updates/sec on FX ticks — the exact figure `scale-numbers.md` calls impossible. Computing 2,500 rows when a screen opens takes microseconds |
| `exposure` per account per currency | a `SUM` query over positions, not a table |
| `account_valuation` | same |
| generic exposure rows | `product.issue_ccy` at 100%, unless `position_exposure` has a `seq 0` row |
| `quantity_change` | today's trades summed |

The pattern: **store what a machine cannot invent.** Prices, rates, holdings, what a human typed,
and what was sent to the outside world. Everything else is a formula.

---

# Dropped tables, and why

| Table | Reason |
|---|---|
| `share_class` | not one of the six features. Hedging an investor's currency is a second, separate problem |
| `manager`, `deal` | the Fund > Manager > Deal tree is not wanted |
| `fx_forward` | forwards are products linked by `contract_ref` |
| `custodian` | a text column on `account` is enough at ~20 values |
| `role`, `user_role` | `entitlement.can_send` covers the one permission that matters |
| `holiday_calendar`, `interest_rate`, `corporate_action`, `exchange_license` | real in production, absent from the six features |
| `load_batch`, `validation_error`, `recon_break`, `audit_log` | operational, not in the six features |
| `hedge_target`, `hedge_preference` | no evidence of a ratio in the real grid; instrument is per hedge |
| `scenario`, `scenario_override` | customising exposure turned out to be saved configuration driving real trades, not a throwaway what-if |
| `pnl`, `fund_nav` | roll-ups of position-level numbers |

---

# Open questions

**Is the generic weight editable in ways `seq 0` cannot express?** The design assumes a user can
change the generic currency and weight. If the real system only allows the weight, `seq 0` still
works but carries a redundant currency.

**Does the UI need to reference a single position directly?** If so, `position` gains a
`position_id` surrogate alongside the natural key. Cost is ~415 MB; currently only one table
points at a position, so it has not been added.
