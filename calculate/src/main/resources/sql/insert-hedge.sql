INSERT INTO hedge (hedge_id, fund_id, currency, hedge_date, exposure_amount,
                   suggested_amount, chosen_amount, instrument, settles_on,
                   status, sent_by, sent_at, external_reference)
VALUES (?, ?, ?, CURRENT_DATE, ?, ?, ?, ?, CURRENT_DATE + 30, 'SENT', ?, now(), ?)
