\if :{?where}
\else
  \set where laptop
\endif

SELECT :'where' = 'cloud' AS running_in_the_cloud \gset

\if :running_in_the_cloud
  \set clients 400
  \set how_big 'THE WHOLE THING   -   16,308,000 positions,  about 25 GB of disk'
\else
  \set clients 40
  \set how_big 'A TENTH OF IT     -    1,630,800 positions,  about 2.5 GB of disk'
\endif

\echo '=============================================='
\echo 'MAKING THE DATA'
\echo '  running on   :' :where
\echo '  size         :' :how_big
\echo '  clients      :' :clients
\echo ''
\echo '  laptop is the default. For the full set:'
\echo '    psql -v where=cloud -f db/3-generate.sql'
\echo '=============================================='


\echo ''
\echo 'STEP 1  -  250,000 shares and swaps'

INSERT INTO product (kind, name, currency, exchange_code, identifier)
SELECT
    CASE WHEN row_number % 10 = 0 THEN 'EQUITY SWAP' ELSE 'SHARES' END,

    (ARRAY['ADRIATIC','NORDWIND','HELIKON','CEDAR','KESTREL','GRANITE',
           'MARLOW','PENNINE','REDSTONE','THORNE','ASHFORD','BRAMBLE'])
      [1 + row_number % 12]
    || ' ' ||
    (ARRAY['METALS','ENERGY','PHARMA','SYSTEMS','SHIPPING','TELECOM',
           'UTILITIES','RESOURCES','MOTORS','FOODS'])
      [1 + (row_number / 12) % 10]
    || ' ' ||
    (ARRAY['PLC','AG','SA','NV','INC','CORP','LTD','GMBH'])
      [1 + (row_number / 120) % 8],

    (ARRAY['USD','EUR','GBP','JPY','CHF','CAD','AUD','NZD','SEK','NOK',
           'DKK','HKD','SGD','ILS','PLN','CZK','CNH','KRW','TWD','INR',
           'BRL','MXN','ZAR','TRY','THB','IDR','PHP','HUF','RUB','XAU'])
      [1 + row_number % 30],

    (ARRAY['XLDN','XFRK','XNYK','XSYD','XTKY','XTOR','XZRH','XOSL','XSTO','XCPH',
           'XAMS','XBRU','XLIS','XMAD','XMIL','XVIE','XWAW','XPRG','XBUD','XHEL'])
      [1 + row_number % 20],

    lpad(row_number::text, 9, '0')

FROM generate_series(1, 250000) AS row_number;


\echo 'STEP 2  -  800,000 currency contracts, two rows each'

INSERT INTO product (kind, name, currency, identifier, settles_on, contract_group)
SELECT
    CASE WHEN row_number % 4 = 0 THEN 'CURRENCY SPOT' ELSE 'FORWARD' END,

    'CURRENCY CONTRACT SETTLING ' || (CURRENT_DATE + (7 + row_number % 180)),

    (ARRAY['USD','EUR','GBP','JPY','CHF','CAD','AUD','NZD','SEK','NOK',
           'DKK','HKD','SGD','ILS','PLN','CZK','CNH','KRW','TWD','INR',
           'BRL','MXN','ZAR','TRY','THB','IDR','PHP','HUF','RUB','XAU'])
      [1 + row_number % 30],

    lpad((250000 + row_number)::text, 9, '0'),

    CURRENT_DATE + (7 + row_number % 180),

    'CONTRACT-' || ((row_number + 1) / 2)

FROM generate_series(1, 800000) AS row_number;


\echo 'STEP 3  -  cash and accrual, one pair for every currency'

INSERT INTO product (kind, name, currency, identifier)
SELECT 'CASH',
       'CASH ' || code,
       code,
       lpad((1050000 + row_number() OVER (ORDER BY code))::text, 9, '0')
FROM currency;

INSERT INTO product (kind, name, currency, identifier)
SELECT 'ACCRUAL',
       'ACCRUAL ' || code,
       code,
       lpad((1060000 + row_number() OVER (ORDER BY code))::text, 9, '0')
FROM currency;


\echo 'STEP 4  -  a starting price for every product'

INSERT INTO price (product_id, price, freshness, price_date, arrived_at)
SELECT product_id,
       CASE WHEN kind IN ('SHARES', 'EQUITY SWAP')
            THEN (random() * 300 + 5)::numeric(20,6)
            ELSE 1.0
       END,
       'DELAYED 20 MINUTES',
       CURRENT_DATE,
       now()
FROM product;


\echo 'STEP 5  -  clients:  60% small,  30% medium,  10% large'

INSERT INTO client (name, size, region)
SELECT
    (ARRAY['Adriatic','Nordwind','Helikon','Cedar','Kestrel',
           'Granite','Marlow','Pennine','Redstone','Thorne'])
      [1 + row_number % 10]
    || ' ' ||
    (ARRAY['Capital','Investments','Asset Management','Partners','Advisors'])
      [1 + row_number % 5]
    || ' ' || row_number,

    CASE
      WHEN row_number <= :clients * 0.6 THEN 'SMALL'
      WHEN row_number <= :clients * 0.9 THEN 'MEDIUM'
      ELSE 'LARGE'
    END,

    (ARRAY['US','US','EUROPE','ASIA'])[1 + row_number % 4]

FROM generate_series(1, :clients) AS row_number;


\echo 'STEP 6  -  funds:  a small client gets 2,  medium 4,  large 9'

INSERT INTO fund (client_id, name, reporting_currency)
SELECT client.client_id,
       'Fund ' || client.client_id || '-' || fund_number,
       (ARRAY['USD','EUR','GBP','JPY','CHF','CAD','AUD','NZD'])
         [1 + (client.client_id + fund_number) % 8]
FROM client
CROSS JOIN generate_series(1,
    CASE client.size
      WHEN 'SMALL'  THEN 2
      WHEN 'MEDIUM' THEN 4
      ELSE 9
    END) AS fund_number;


\echo 'STEP 7  -  accounts:  a small fund gets 4,  medium 8,  large 13'

INSERT INTO account (fund_id, name, custodian)
SELECT fund.fund_id,
       'Account ' || fund.fund_id || '-' || account_number,
       CASE WHEN account_number % 2 = 0
            THEN 'Custodian ' || (account_number % 20)
            ELSE NULL
       END
FROM fund
JOIN client ON client.client_id = fund.client_id
CROSS JOIN generate_series(1,
    CASE client.size
      WHEN 'SMALL'  THEN 4
      WHEN 'MEDIUM' THEN 8
      ELSE 13
    END) AS account_number;


\echo 'STEP 8  -  5 people per client, and what they are allowed to see'

INSERT INTO app_user (user_id, client_id, name, email)
SELECT 'user' || (client.client_id * 10 + person_number),
       client.client_id,
       (ARRAY['A. Fischer','R. Baumann','M. Delgado','S. Okonkwo','L. Petrova'])
         [person_number],
       'user' || (client.client_id * 10 + person_number) || '@example.com'
FROM client
CROSS JOIN generate_series(1, 5) AS person_number;

INSERT INTO entitlement (user_id, fund_id, can_send_trades)
SELECT app_user.user_id,
       fund.fund_id,
       app_user.user_id LIKE '%1'
FROM app_user
JOIN fund ON fund.client_id = app_user.client_id;


\echo 'STEP 9  -  positions:  400 per small account,  1,000 medium,  2,500 large'
\echo '          this is the big one'

INSERT INTO position (account_id, product_id, quantity, cost,
                      is_hedge, position_date)
SELECT account.account_id,

       1 + ((account.account_id * 100 + position_number) % 250000),

       (random() * 500000 + 1)::numeric(20,4),
       (random() * 5000000 + 1)::numeric(20,4),

       random() < 0.02,

       CURRENT_DATE

FROM account
JOIN fund   ON fund.fund_id     = account.fund_id
JOIN client ON client.client_id = fund.client_id
CROSS JOIN generate_series(1,
    CASE client.size
      WHEN 'SMALL'  THEN 400
      WHEN 'MEDIUM' THEN 1000
      ELSE 2500
    END) AS position_number;


\echo 'STEP 10  -  extra currency exposures on 1 position in every 97'

INSERT INTO position_exposure (account_id, product_id, slot, currency, percentage)
SELECT account_id,
       product_id,
       1,
       (ARRAY['CHF','USD','EUR','JPY'])[1 + product_id % 4],
       10 + (product_id % 40)
FROM position
WHERE product_id % 97 = 0;


\echo ''
\echo '=============================================='
\echo 'FINISHED'
\echo '=============================================='
