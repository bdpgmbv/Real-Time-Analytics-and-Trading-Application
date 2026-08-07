INSERT INTO file_load (file_name, fingerprint, custodian, arrived_how,
                       rows_in_file, rows_loaded, rows_rejected, started_at)
VALUES (?, ?, ?, ?, ?, 0, 0, ?)
RETURNING file_load_id
