--liquibase formatted sql

--changeset vyshaliprabananthlal:003-add-client-id
--comment Row level security needs to know whose row this is without a join. Denormalised on
--comment purpose: a policy that joins three tables is a policy nobody can afford to run.
--rollback ALTER TABLE position DROP COLUMN client_id;
--rollback ALTER TABLE position_exposure DROP COLUMN client_id;
--rollback ALTER TABLE trade DROP COLUMN client_id;
--rollback ALTER TABLE hedge DROP COLUMN client_id;
--rollback ALTER TABLE hedge_fill DROP COLUMN client_id;
--rollback ALTER TABLE account DROP COLUMN client_id;

ALTER TABLE account           ADD COLUMN client_id INTEGER;
ALTER TABLE position          ADD COLUMN client_id INTEGER;
ALTER TABLE position_exposure ADD COLUMN client_id INTEGER;
ALTER TABLE trade             ADD COLUMN client_id INTEGER;
ALTER TABLE hedge             ADD COLUMN client_id INTEGER;
ALTER TABLE hedge_fill        ADD COLUMN client_id INTEGER;


--changeset vyshaliprabananthlal:003-backfill-client-id
--comment Fill it in from the tree that already knows the answer.

UPDATE account a SET client_id = f.client_id FROM fund f WHERE f.fund_id = a.fund_id;

UPDATE position p SET client_id = a.client_id FROM account a WHERE a.account_id = p.account_id;

UPDATE position_exposure e SET client_id = a.client_id
  FROM account a WHERE a.account_id = e.account_id;

UPDATE trade t SET client_id = a.client_id FROM account a WHERE a.account_id = t.account_id;

UPDATE hedge h SET client_id = f.client_id FROM fund f WHERE f.fund_id = h.fund_id;

UPDATE hedge_fill hf SET client_id = h.client_id FROM hedge h WHERE h.hedge_id = hf.hedge_id;


--changeset vyshaliprabananthlal:003-client-id-required
--comment Now that every row has one, refuse a row that does not.

ALTER TABLE account           ALTER COLUMN client_id SET NOT NULL;
ALTER TABLE position          ALTER COLUMN client_id SET NOT NULL;
ALTER TABLE position_exposure ALTER COLUMN client_id SET NOT NULL;
ALTER TABLE trade             ALTER COLUMN client_id SET NOT NULL;
ALTER TABLE hedge             ALTER COLUMN client_id SET NOT NULL;
ALTER TABLE hedge_fill        ALTER COLUMN client_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS position_client_id_idx ON position(client_id);
CREATE INDEX IF NOT EXISTS trade_client_id_idx ON trade(client_id);
