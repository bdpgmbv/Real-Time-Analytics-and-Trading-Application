package vyshaliprabananthlal.jobs.exposure;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HoldingsLoader {

  static final String THE_QUESTION =
      "SELECT pos.product_id, acc.fund_id, prod.currency, sum(pos.quantity)"
          + "  FROM position pos"
          + "  JOIN account acc ON acc.account_id = pos.account_id"
          + "  JOIN product prod ON prod.product_id = pos.product_id"
          + " GROUP BY pos.product_id, acc.fund_id, prod.currency";

  private HoldingsLoader() {}

  public static Map<Integer, List<FundHolding>> from(String url, String user, String password)
      throws SQLException {

    Map<Integer, List<FundHolding>> whoHoldsWhat = new HashMap<>();

    try (Connection database = DriverManager.getConnection(url, user, password);
        Statement asking = database.createStatement();
        ResultSet answer = asking.executeQuery(THE_QUESTION)) {

      asking.setFetchSize(10000);

      while (answer.next()) {
        int productId = answer.getInt(1);
        FundHolding holder =
            new FundHolding(answer.getInt(2), answer.getString(3).trim(), answer.getDouble(4));

        whoHoldsWhat.computeIfAbsent(productId, which -> new ArrayList<>()).add(holder);
      }
    }
    return whoHoldsWhat;
  }

  public static int holdingCount(Map<Integer, List<FundHolding>> whoHoldsWhat) {
    int total = 0;
    for (List<FundHolding> holders : whoHoldsWhat.values()) {
      total = total + holders.size();
    }
    return total;
  }
}
