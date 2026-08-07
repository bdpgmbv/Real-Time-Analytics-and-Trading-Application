package vyshaliprabananthlal.ingest.format;

import org.springframework.stereotype.Component;

@Component
public class PipeFormat implements CustodianFormat {

  private static final String HEADING = "SECURITY|PORTFOLIO|BOOK_COST|UNITS";

  @Override
  public String custodianName() {
    return "Halloway Bank";
  }

  @Override
  public boolean looksLikeMine(String headingLine) {
    return HEADING.equalsIgnoreCase(headingLine.trim());
  }

  @Override
  public PositionRow readOneLine(String line) {
    String[] cells = line.split("\\|", -1);

    if (cells.length != 4) {
      throw new BadLine("expected 4 values separated by pipes, found " + cells.length);
    }

    String identifier = cells[0].trim();
    String accountName = cells[1].trim();

    if (accountName.isEmpty()) {
      throw new BadLine("the portfolio name is empty");
    }
    if (identifier.length() != 9) {
      throw new BadLine("the security must be 9 characters, found " + identifier.length());
    }

    return new PositionRow(
        accountName,
        identifier,
        CommaFormat.readNumber(cells[3], "units"),
        CommaFormat.readNumber(cells[2], "book cost"));
  }
}
