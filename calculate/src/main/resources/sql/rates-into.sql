SELECT from_currency, rate
  FROM fx_rate
 WHERE to_currency = ?
   AND rate_date = CURRENT_DATE
