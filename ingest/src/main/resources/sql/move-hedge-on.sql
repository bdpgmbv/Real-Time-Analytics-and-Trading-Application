UPDATE hedge
   SET status = CASE
         WHEN (SELECT coalesce(sum(amount_filled), 0)
                 FROM hedge_fill f
                WHERE f.hedge_id = hedge.hedge_id) >= abs(hedge.chosen_amount)
         THEN 'FILLED'
         ELSE 'PARTIALLY FILLED'
       END
 WHERE hedge_id = ?
