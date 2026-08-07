INSERT INTO hedge_fill (fill_id, hedge_id, amount_filled, rate_we_got,
                        filled_at, their_reference)
VALUES (?, ?, ?, ?, ?, ?)
ON CONFLICT (fill_id) DO NOTHING
