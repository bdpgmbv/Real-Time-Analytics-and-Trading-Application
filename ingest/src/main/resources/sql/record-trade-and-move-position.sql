WITH newly_recorded AS (
  INSERT INTO trade (trade_id, account_id, product_id, how_many, price,
                     happened_at, trade_date, came_from)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  ON CONFLICT (trade_id) DO NOTHING
  RETURNING account_id, product_id, how_many
)
UPDATE position
   SET how_many = position.how_many + newly_recorded.how_many
  FROM newly_recorded
 WHERE position.account_id = newly_recorded.account_id
   AND position.product_id = newly_recorded.product_id
