INSERT INTO hedge (hedge_id, fund_id, currency, hedge_date, exposure_amount,
                   we_suggested, client_chose, instrument, settles_on,
                   status, sent_by, sent_at, their_reference)
VALUES (?, ?, ?, CURRENT_DATE, ?, ?, ?, ?, CURRENT_DATE + 30, 'SENT', ?, now(), ?)
