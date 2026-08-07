UPDATE price
   SET price = ?,
       arrived_at = now()
 WHERE product_id = ?
