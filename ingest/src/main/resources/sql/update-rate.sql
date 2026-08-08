UPDATE fx_rate
   SET rate = ?,
       source = 'LIVE TICK'
 WHERE from_currency = ?
   AND to_currency = ?
   AND rate_date = CURRENT_DATE
