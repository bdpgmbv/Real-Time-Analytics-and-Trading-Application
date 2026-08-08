--liquibase formatted sql

--changeset vyshaliprabananthlal:004-reader-role
--comment The service reading on behalf of one client is not the service writing for all of
--comment them. A table owner bypasses row level security, so the reader must not be an owner.
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT count(*) FROM pg_roles WHERE rolname='rtat_reader'
--rollback DROP ROLE IF EXISTS rtat_reader;

CREATE ROLE rtat_reader NOLOGIN;


--changeset vyshaliprabananthlal:004-reader-grants
--comment It may read everything the policies allow, and write only what a person approves.
--rollback REVOKE ALL ON ALL TABLES IN SCHEMA public FROM rtat_reader;

GRANT USAGE ON SCHEMA public TO rtat_reader;

GRANT SELECT ON ALL TABLES IN SCHEMA public TO rtat_reader;

GRANT INSERT, UPDATE ON hedge TO rtat_reader;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO rtat_reader;


--changeset vyshaliprabananthlal:004-enable-row-level-security
--comment Postgres decides who sees a row, not a WHERE clause somebody has to remember.
--comment A query that forgets the filter returns nothing now instead of everything.
--rollback ALTER TABLE position DISABLE ROW LEVEL SECURITY;

ALTER TABLE account ENABLE ROW LEVEL SECURITY;

ALTER TABLE position ENABLE ROW LEVEL SECURITY;

ALTER TABLE position_exposure ENABLE ROW LEVEL SECURITY;

ALTER TABLE trade ENABLE ROW LEVEL SECURITY;

ALTER TABLE hedge ENABLE ROW LEVEL SECURITY;

ALTER TABLE hedge_fill ENABLE ROW LEVEL SECURITY;

ALTER TABLE fund ENABLE ROW LEVEL SECURITY;

ALTER TABLE client ENABLE ROW LEVEL SECURITY;


--changeset vyshaliprabananthlal:004-policies
--comment One policy, one sentence, on every table a client owns.
--rollback DROP POLICY IF EXISTS one_client_only ON position;

CREATE POLICY one_client_only ON account USING (client_id = current_setting('rtat.client_id', true)::int);

CREATE POLICY one_client_only ON position USING (client_id = current_setting('rtat.client_id', true)::int);

CREATE POLICY one_client_only ON position_exposure USING (client_id = current_setting('rtat.client_id', true)::int);

CREATE POLICY one_client_only ON trade USING (client_id = current_setting('rtat.client_id', true)::int);

CREATE POLICY one_client_only ON hedge USING (client_id = current_setting('rtat.client_id', true)::int);

CREATE POLICY one_client_only ON hedge_fill USING (client_id = current_setting('rtat.client_id', true)::int);

CREATE POLICY one_client_only ON fund USING (client_id = current_setting('rtat.client_id', true)::int);

CREATE POLICY one_client_only ON client USING (client_id = current_setting('rtat.client_id', true)::int);
