\echo '=============================================='
\echo 'CHECKING THE DATA'
\echo '=============================================='

\echo ''
\echo 'HOW MANY ROWS IN EACH TABLE'
SELECT 'currency'          AS table_name, count(*) FROM currency
UNION ALL SELECT 'exchange',          count(*) FROM exchange
UNION ALL SELECT 'fx_rate',           count(*) FROM fx_rate
UNION ALL SELECT 'product',           count(*) FROM product
UNION ALL SELECT 'price',             count(*) FROM price
UNION ALL SELECT 'client',            count(*) FROM client
UNION ALL SELECT 'fund',              count(*) FROM fund
UNION ALL SELECT 'account',           count(*) FROM account
UNION ALL SELECT 'position',          count(*) FROM position
UNION ALL SELECT 'position_exposure', count(*) FROM position_exposure
UNION ALL SELECT 'app_user',          count(*) FROM app_user
UNION ALL SELECT 'entitlement',       count(*) FROM entitlement
ORDER BY 1;

\echo ''
\echo 'CHECK 1  -  does every fund belong to a real client?   (want 0)'
SELECT count(*) AS funds_with_no_client
FROM fund
LEFT JOIN client ON client.client_id = fund.client_id
WHERE client.client_id IS NULL;

\echo 'CHECK 2  -  does every account belong to a real fund?   (want 0)'
SELECT count(*) AS accounts_with_no_fund
FROM account
LEFT JOIN fund ON fund.fund_id = account.fund_id
WHERE fund.fund_id IS NULL;

\echo 'CHECK 3  -  does every position point at a real account?   (want 0)'
SELECT count(*) AS positions_with_no_account
FROM position
LEFT JOIN account ON account.account_id = position.account_id
WHERE account.account_id IS NULL;

\echo 'CHECK 4  -  does every position point at a real product?   (want 0)'
SELECT count(*) AS positions_with_no_product
FROM position
LEFT JOIN product ON product.product_id = position.product_id
WHERE product.product_id IS NULL;

\echo 'CHECK 5  -  does every product have a price?   (want 0)'
SELECT count(*) AS products_with_no_price
FROM product
LEFT JOIN price ON price.product_id = product.product_id
WHERE price.product_id IS NULL;

\echo 'CHECK 6  -  is every product identifier different?   (want 0 repeated)'
SELECT count(*) AS total_products,
       count(DISTINCT identifier) AS different_identifiers,
       count(*) - count(DISTINCT identifier) AS repeated
FROM product;

\echo 'CHECK 7  -  which currencies do not have 2 decimal places?'
SELECT code, name, decimal_places
FROM currency
WHERE decimal_places <> 2
ORDER BY code;

\echo 'CHECK 8  -  are the exchange rates sensible?'
\echo '            one dollar should buy about 158 yen'
SELECT from_currency,
       rate,
       round(1 / rate, 2) AS how_many_you_get_for_one_dollar
FROM fx_rate
WHERE to_currency = 'USD'
  AND from_currency IN ('JPY', 'KRW', 'GBP', 'HKD', 'EUR')
ORDER BY from_currency;

\echo 'CHECK 9  -  is a currency against itself exactly 1?   (want 0 wrong)'
SELECT count(*) AS wrong
FROM fx_rate
WHERE from_currency = to_currency
  AND rate <> 1;

\echo 'CHECK 10  -  do the big clients hold most of the positions?'
\echo '             large should be about 72%'
SELECT client.size,
       count(DISTINCT client.client_id) AS how_many_clients,
       count(*) AS how_many_positions,
       round(100.0 * count(*) / sum(count(*)) OVER (), 1) AS percent
FROM position
JOIN account ON account.account_id = position.account_id
JOIN fund    ON fund.fund_id       = account.fund_id
JOIN client  ON client.client_id   = fund.client_id
GROUP BY client.size
ORDER BY how_many_positions DESC;

\echo 'CHECK 11  -  does each account have the right number of positions?'
SELECT client.size,
       min(counted) AS fewest,
       max(counted) AS most
FROM (SELECT account_id, count(*) AS counted FROM position GROUP BY account_id) AS totals
JOIN account ON account.account_id = totals.account_id
JOIN fund    ON fund.fund_id       = account.fund_id
JOIN client  ON client.client_id   = fund.client_id
GROUP BY client.size
ORDER BY most DESC;

\echo 'CHECK 12  -  what kinds of product do we have?'
SELECT kind, count(*) FROM product GROUP BY kind ORDER BY count(*) DESC;

\echo 'CHECK 13  -  does every currency contract have exactly 2 rows?'
SELECT count(*) AS how_many_contracts,
       min(rows_found) AS fewest_rows,
       max(rows_found) AS most_rows
FROM (SELECT contract_group, count(*) AS rows_found
      FROM product
      WHERE contract_group IS NOT NULL
      GROUP BY contract_group) AS contracts;

\echo 'CHECK 14  -  can any person see another company''s fund?   (want 0)'
SELECT count(*) AS leaks
FROM entitlement
JOIN app_user ON app_user.user_id = entitlement.user_id
JOIN fund     ON fund.fund_id     = entitlement.fund_id
WHERE fund.client_id <> app_user.client_id;

\echo 'CHECK 15  -  how much space is being used?'
SELECT relname AS table_name,
       pg_size_pretty(pg_total_relation_size(pg_class.oid)) AS size
FROM pg_class
JOIN pg_namespace ON pg_namespace.oid = pg_class.relnamespace
WHERE pg_namespace.nspname = 'public'
  AND pg_class.relkind = 'r'
ORDER BY pg_total_relation_size(pg_class.oid) DESC
LIMIT 6;
