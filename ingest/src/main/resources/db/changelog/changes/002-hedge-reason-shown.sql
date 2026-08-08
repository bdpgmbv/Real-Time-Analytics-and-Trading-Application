--liquibase formatted sql

--changeset vyshaliprabananthlal:002-hedge-reason-shown
--comment A hedge is sent by a person who answers for it later. Record what they were shown.
--rollback ALTER TABLE hedge DROP COLUMN reason_shown;

ALTER TABLE hedge ADD COLUMN reason_shown TEXT;
