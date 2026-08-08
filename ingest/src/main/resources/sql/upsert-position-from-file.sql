INSERT INTO position (account_id, product_id, quantity, cost,
                      is_hedge, position_date)
SELECT a.account_id, p.product_id, ?, ?, false, CURRENT_DATE
  FROM account a, product p
 WHERE a.name = ?
   AND p.identifier = ?
ON CONFLICT (account_id, product_id)
DO UPDATE SET quantity = EXCLUDED.quantity,
              cost = EXCLUDED.cost
