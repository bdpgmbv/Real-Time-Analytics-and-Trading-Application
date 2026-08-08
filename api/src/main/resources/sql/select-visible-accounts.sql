SELECT a.account_id
  FROM account a
  JOIN entitlement e ON e.fund_id = a.fund_id
 WHERE e.user_id = ?
   AND a.fund_id = ?
   AND a.account_id = ANY (?)
 ORDER BY a.account_id
