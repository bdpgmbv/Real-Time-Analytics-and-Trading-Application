WITH what_we_hold AS (
  SELECT pos.account_id,
         pos.product_id,
         prod.currency AS product_currency,
         pos.how_many * coalesce(pos.price_typed_in, latest.price) AS market_value
    FROM position pos
    JOIN product prod ON prod.product_id = pos.product_id
    JOIN price latest ON latest.product_id = pos.product_id
                     AND latest.price_date = CURRENT_DATE
   WHERE pos.account_id = ANY (?)
),
generic_exposure AS (
  SELECT product_currency AS currency,
         market_value AS amount
    FROM what_we_hold
),
specific_exposure AS (
  SELECT extra.currency,
         held.market_value * extra.percentage / 100 AS amount
    FROM what_we_hold held
    JOIN position_exposure extra ON extra.account_id = held.account_id
                                AND extra.product_id = held.product_id
),
every_exposure AS (
  SELECT * FROM generic_exposure
  UNION ALL
  SELECT * FROM specific_exposure
)
SELECT currency,
       sum(amount) AS exposure
  FROM every_exposure
 GROUP BY currency
 ORDER BY currency
