INSERT INTO hedge_fill (fill_id, hedge_id, amount_filled, fill_rate,
                        filled_at, external_reference)
VALUES (?, ?, ?, ?, ?, ?)
ON CONFLICT (fill_id) DO NOTHING
