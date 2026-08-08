SELECT line_number, reason
  FROM file_row_problem
 WHERE file_load_id = ?
 ORDER BY line_number
