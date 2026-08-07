DROP TABLE IF EXISTS position_exposure, position, price, trade, hedge_fill, hedge,
  entitlement, app_user, account, fund, client, product, fx_rate, exchange, currency CASCADE;


CREATE TABLE currency (
  code          CHAR(3)  PRIMARY KEY,
  name          TEXT     NOT NULL,
  decimal_places SMALLINT NOT NULL
);


CREATE TABLE exchange (
  code CHAR(4) PRIMARY KEY,
  name TEXT    NOT NULL
);


CREATE TABLE fx_rate (
  from_currency CHAR(3)        NOT NULL REFERENCES currency,
  to_currency   CHAR(3)        NOT NULL REFERENCES currency,
  rate          NUMERIC(20,10) NOT NULL,
  rate_date     DATE           NOT NULL,
  where_from    TEXT           NOT NULL,
  PRIMARY KEY (from_currency, to_currency, rate_date)
);


CREATE TABLE product (
  product_id     INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  kind           TEXT    NOT NULL,
  name           TEXT    NOT NULL,
  currency       CHAR(3) NOT NULL REFERENCES currency,
  exchange_code  CHAR(4) REFERENCES exchange,
  identifier     CHAR(9) NOT NULL,
  settles_on     DATE,
  contract_group TEXT
);


CREATE TABLE client (
  client_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name      TEXT NOT NULL,
  size      TEXT NOT NULL,
  region    TEXT NOT NULL
);


CREATE TABLE fund (
  fund_id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  client_id          INTEGER NOT NULL REFERENCES client,
  name               TEXT    NOT NULL,
  reporting_currency CHAR(3) NOT NULL REFERENCES currency
);


CREATE TABLE account (
  account_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  fund_id    INTEGER NOT NULL REFERENCES fund,
  name       TEXT    NOT NULL,
  custodian  TEXT
);


CREATE TABLE position (
  account_id       INTEGER       NOT NULL,
  product_id       INTEGER       NOT NULL,
  how_many         NUMERIC(20,4) NOT NULL,
  what_we_paid     NUMERIC(20,4) NOT NULL,
  is_a_hedge       BOOLEAN       NOT NULL,
  price_typed_in   NUMERIC(20,6),
  comments         TEXT,
  position_date    DATE          NOT NULL
);


CREATE TABLE position_exposure (
  account_id INTEGER      NOT NULL,
  product_id INTEGER      NOT NULL,
  slot       SMALLINT     NOT NULL,
  currency   CHAR(3)      NOT NULL REFERENCES currency,
  percentage NUMERIC(9,4) NOT NULL
);


CREATE TABLE price (
  product_id   INTEGER       NOT NULL,
  price        NUMERIC(20,6) NOT NULL,
  how_fresh    TEXT          NOT NULL,
  price_date   DATE          NOT NULL,
  arrived_at   TIMESTAMPTZ   NOT NULL
);


CREATE TABLE trade (
  trade_id    BIGINT        PRIMARY KEY,
  account_id  INTEGER       NOT NULL REFERENCES account,
  product_id  INTEGER       NOT NULL REFERENCES product,
  how_many    NUMERIC(20,4) NOT NULL,
  price       NUMERIC(20,6) NOT NULL,
  happened_at TIMESTAMPTZ   NOT NULL,
  trade_date  DATE          NOT NULL,
  came_from   TEXT          NOT NULL
);


CREATE TABLE hedge (
  hedge_id           BIGINT        PRIMARY KEY,
  fund_id            INTEGER       NOT NULL REFERENCES fund,
  currency           CHAR(3)       NOT NULL REFERENCES currency,
  hedge_date         DATE          NOT NULL,
  exposure_amount    NUMERIC(20,4) NOT NULL,
  we_suggested       NUMERIC(20,4) NOT NULL,
  client_chose       NUMERIC(20,4) NOT NULL,
  instrument         TEXT          NOT NULL,
  settles_on         DATE          NOT NULL,
  status             TEXT          NOT NULL,
  sent_by            TEXT,
  sent_at            TIMESTAMPTZ,
  their_reference    TEXT
);


CREATE TABLE hedge_fill (
  fill_id         BIGINT         PRIMARY KEY,
  hedge_id        BIGINT         NOT NULL REFERENCES hedge,
  amount_filled   NUMERIC(20,4)  NOT NULL,
  rate_we_got     NUMERIC(20,10) NOT NULL,
  filled_at       TIMESTAMPTZ    NOT NULL,
  their_reference TEXT
);


CREATE TABLE app_user (
  user_id   TEXT    PRIMARY KEY,
  client_id INTEGER REFERENCES client,
  name      TEXT    NOT NULL,
  email     TEXT    NOT NULL
);


CREATE TABLE entitlement (
  user_id       TEXT    NOT NULL REFERENCES app_user,
  fund_id       INTEGER NOT NULL REFERENCES fund,
  can_send_trades BOOLEAN NOT NULL,
  PRIMARY KEY (user_id, fund_id)
);
