UPDATE hedge
   SET status = CASE
         WHEN (SELECT coalesce(sum(amount_filled), 0)
                 FROM hedge_fill f
                WHERE f.hedge_id = hedge.hedge_id) >= abs(hedge.client_chose)
         THEN 'FILLED'
         ELSE 'PARTIALLY FILLED'
       END
 WHERE hedge_id = ?
