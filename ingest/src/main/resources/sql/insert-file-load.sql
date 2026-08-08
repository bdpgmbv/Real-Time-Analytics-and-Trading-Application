INSERT INTO file_load (file_name, fingerprint, custodian, arrival_method,
                       total_rows, rows_loaded, rows_rejected, started_at)
VALUES (?, ?, ?, ?, ?, 0, 0, ?)
RETURNING file_load_id
