\echo '--- adding keys and indexes, now that the data is loaded ---'
ALTER TABLE position
  ADD PRIMARY KEY (account_id, product_id);

ALTER TABLE position
  ADD FOREIGN KEY (account_id) REFERENCES account;

ALTER TABLE position
  ADD FOREIGN KEY (product_id) REFERENCES product;

ALTER TABLE position_exposure
  ADD PRIMARY KEY (account_id, product_id, slot);

ALTER TABLE price
  ADD PRIMARY KEY (product_id, price_date);

ALTER TABLE price
  ADD FOREIGN KEY (product_id) REFERENCES product;

CREATE INDEX ON position(product_id);

CREATE INDEX ON position(is_hedge);

CREATE INDEX ON account (fund_id);

CREATE INDEX ON fund (client_id);

CREATE INDEX ON product (kind);

CREATE INDEX ON entitlement (fund_id);

ANALYZE;
