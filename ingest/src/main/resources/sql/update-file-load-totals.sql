UPDATE file_load
   SET rows_loaded = ?,
       rows_rejected = ?,
       finished_at = ?
 WHERE file_load_id = ?
