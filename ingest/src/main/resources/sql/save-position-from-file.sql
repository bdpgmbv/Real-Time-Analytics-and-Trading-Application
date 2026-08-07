INSERT INTO position (account_id, product_id, how_many, what_we_paid,
                      is_a_hedge, position_date)
SELECT a.account_id, p.product_id, ?, ?, false, CURRENT_DATE
  FROM account a, product p
 WHERE a.name = ?
   AND p.identifier = ?
ON CONFLICT (account_id, product_id)
DO UPDATE SET how_many = EXCLUDED.how_many,
              what_we_paid = EXCLUDED.what_we_paid
