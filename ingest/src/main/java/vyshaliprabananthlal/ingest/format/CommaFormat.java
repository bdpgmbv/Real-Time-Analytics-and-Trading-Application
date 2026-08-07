package vyshaliprabananthlal.ingest.format;

import org.springframework.stereotype.Component;

@Component
public class CommaFormat implements CustodianFormat {

  private static final String HEADING = "account,identifier,quantity,cost";

  @Override
  public String custodianName() {
    return "Northgate Trust";
  }

  @Override
  public boolean looksLikeMine(String headingLine) {
    return HEADING.equalsIgnoreCase(headingLine.trim());
  }

  @Override
  public PositionRow readOneLine(String line) {
    String[] cells = line.split(",", -1);

    if (cells.length != 4) {
      throw new BadLine("expected 4 values separated by commas, found " + cells.length);
    }

    String accountName = cells[0].trim();
    String identifier = cells[1].trim();

    if (accountName.isEmpty()) {
      throw new BadLine("the account name is empty");
    }
    if (identifier.length() != 9) {
      throw new BadLine("the identifier must be 9 characters, found " + identifier.length());
    }

    return new PositionRow(
        accountName, identifier, readNumber(cells[2], "quantity"), readNumber(cells[3], "cost"));
  }

  static double readNumber(String cell, String whatItIs) {
    try {
      return Double.parseDouble(cell.trim());
    } catch (NumberFormatException notANumber) {
      throw new BadLine("the " + whatItIs + " is not a number: " + cell.trim());
    }
  }
}
