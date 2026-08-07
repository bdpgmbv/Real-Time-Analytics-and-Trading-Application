SELECT can_send_trades
  FROM entitlement
 WHERE user_id = ?
   AND fund_id = ?
