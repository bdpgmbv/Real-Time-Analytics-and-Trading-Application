SELECT line_number, what_is_wrong
  FROM file_row_problem
 WHERE file_load_id = ?
 ORDER BY line_number
