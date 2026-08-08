SELECT f.fund_id, f.name, f.reporting_currency, e.can_send_trades
  FROM entitlement e
  JOIN fund f ON f.fund_id = e.fund_id
 WHERE e.user_id = ?
 ORDER BY f.fund_id
