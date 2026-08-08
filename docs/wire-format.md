# What to send us

Five Kafka topics, one HTTP endpoint, one folder. This is the contract. It is pinned by
`WireFormatTest` in the ingest module, so it cannot drift without a test failing.

## The wire is not the schema

The database column is `quantity`. The JSON field is `howMany`. That is deliberate.

Renaming a column is our business. Renaming a field on the wire breaks every integration a
client has written. The two are allowed to differ and will keep differing.

## Kafka

| topic | key | why that key |
|---|---|---|
| `rtat.price` | `productId` | one product stays on one partition, so its prices stay in order |
| `rtat.position` | `accountId` | one account stays in order |
| `rtat.trade` | `accountId` | |
| `rtat.fx-rate` | `from-to`, e.g. `EUR-USD` | one pair stays in order |
| `rtat.hedge-fill` | `hedgeId` | both halves of a split fill stay in order |

```json
rtat.price       {"productId":100101,"price":189.32,"howFresh":"DELAYED 20 MINUTES"}
rtat.position    {"accountId":340,"productId":100102,"howMany":500.25}
rtat.trade       {"tradeId":77,"accountId":340,"productId":100102,"howMany":-500,
                  "price":42.5,"happenedAt":"2026-08-06T14:32:11Z","cameFrom":"AUTOMATIC FEED"}
rtat.fx-rate     {"from":"EUR","to":"USD","rate":1.1542}
rtat.hedge-fill  {"fillId":1,"hedgeId":500,"amountFilled":9000000,"rate":1.1540,
                  "filledAt":"2026-08-06T14:32:11Z","theirReference":"FXM-77120-1"}
```

**A field we do not recognise is ignored.** You can add one without waiting for us.

**A trade is recorded once, however many times you send it.** `tradeId` is the key. The same
is true of `fillId`. Resend freely after a network problem; nothing doubles.

## Files

`POST /upload` with `file=@yourfile.csv`, or drop the file in the SFTP folder.

Two layouts are understood, chosen by the heading line:

```
account,identifier,quantity,cost
ACCOUNT-1,000000001,500,12000

SECURITY|PORTFOLIO|BOOK_COST|UNITS
000000001|ACCOUNT-1|45000|900
```

Note the second one puts cost before quantity. Adding a third layout is one class on our side.

**The file is recognised by its contents, not its name.** Send the same file twice under two
names and it loads once. Change one number and it is a new file.

**One bad row does not lose the file.** Good rows load, bad ones come back with the line
number and what was wrong:

```json
{"rowsLoaded": 47, "rowsRejected": 3,
 "problems": ["line 12: the quantity is not a number: ABC",
              "line 30: the identifier must be 9 characters, found 5"]}
```

## What we do not accept

Nothing without a token. See the auth section of `deploy/README.md`.
