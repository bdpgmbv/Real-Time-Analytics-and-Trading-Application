WITH newly_recorded AS (
  INSERT INTO trade (trade_id, account_id, product_id, quantity, price,
                     happened_at, trade_date, source)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  ON CONFLICT (trade_id) DO NOTHING
  RETURNING account_id, product_id, quantity
)
UPDATE position
   SET quantity = position.quantity + newly_recorded.quantity
  FROM newly_recorded
 WHERE position.account_id = newly_recorded.account_id
   AND position.product_id = newly_recorded.product_id
