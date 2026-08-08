#!/bin/bash
#
# Sets up a new client: the client, its funds, its accounts, and one person who can sign in.
#
#   ./deploy/operate/new-client.sh "Adriatic Capital" EUROPE 3
#
# Everything it creates carries the new client_id, so row level security keeps it apart from
# every other client from the first row onwards.

set -euo pipefail

name="${1:?usage: new-client.sh <name> <region> <how-many-funds>}"
region="${2:?region, one of US EUROPE ASIA}"
howManyFunds="${3:-1}"

container="${RTAT_POSTGRES:-rtat-postgres}"
user="${RTAT_DB_USER:-rtat}"
database="${RTAT_DB_NAME:-rtat}"

docker exec -i "$container" psql -U "$user" -d "$database" -v ON_ERROR_STOP=1 <<SQL
BEGIN;

INSERT INTO client (name, size, region)
VALUES ('${name//\'/\'\'}', CASE WHEN $howManyFunds >= 6 THEN 'LARGE'
                                WHEN $howManyFunds >= 3 THEN 'MEDIUM'
                                ELSE 'SMALL' END, '$region');

CREATE TEMP TABLE the_new_client AS
SELECT client_id FROM client ORDER BY client_id DESC LIMIT 1;

INSERT INTO fund (client_id, name, reporting_currency)
SELECT client_id, '${name//\'/\'\'} Fund ' || n, 'USD'
  FROM the_new_client CROSS JOIN generate_series(1, $howManyFunds) AS n;

INSERT INTO account (fund_id, name, client_id)
SELECT f.fund_id, f.name || ' Account ' || n, f.client_id
  FROM fund f
  JOIN the_new_client c ON c.client_id = f.client_id
 CROSS JOIN generate_series(1, 2) AS n;

INSERT INTO app_user (user_id, client_id, name, email)
SELECT 'admin@' || client_id, client_id, '${name//\'/\'\'} administrator',
       'admin' || client_id || '@example.com'
  FROM the_new_client;

-- The first person may trade. Everybody after them is added by hand, on purpose.
INSERT INTO entitlement (user_id, fund_id, can_send_trades)
SELECT 'admin@' || c.client_id, f.fund_id, true
  FROM the_new_client c JOIN fund f ON f.client_id = c.client_id;

SELECT 'client_id'   AS what, client_id::text AS value FROM the_new_client
UNION ALL
SELECT 'funds',   count(*)::text FROM fund f JOIN the_new_client c ON c.client_id = f.client_id
UNION ALL
SELECT 'accounts', count(*)::text FROM account a JOIN the_new_client c ON c.client_id = a.client_id
UNION ALL
SELECT 'sign in as', 'admin@' || client_id FROM the_new_client;

COMMIT;
SQL

echo
echo "Now create them in the identity provider with the same username, and they can sign in."
