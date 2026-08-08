package vyshaliprabananthlal.ingest.format;

import org.springframework.stereotype.Component;

/**
 * Halloway Bank: pipe separated, security first, and cost before units.
 *
 * <p>The column order is the opposite way round from Northgate. That is the whole reason two
 * formats exist rather than one with a separator setting.
 */
@Component
public class PipeFormat implements CustodianFormat {

    private static final String HEADING = "SECURITY|PORTFOLIO|BOOK_COST|UNITS";
    private static final int SECURITY = 0;
    private static final int PORTFOLIO = 1;
    private static final int BOOK_COST = 2;
    private static final int UNITS = 3;

    @Override
    public String custodianName() {
        return "Halloway Bank";
    }

    @Override
    public boolean matches(String headingLine) {
        return HEADING.equalsIgnoreCase(headingLine.trim());
    }

    @Override
    public PositionRow readOneLine(String line) {
        String[] cells = line.split("\\|", -1);

        if (cells.length != 4) {
            throw new BadLine("expected 4 values separated by pipes, found " + cells.length);
        }

        String identifier = cells[SECURITY].trim();
        String accountName = cells[PORTFOLIO].trim();
        CustodianFormat.checkAccountAndIdentifier(accountName, identifier, "portfolio name");

        return new PositionRow(
                accountName,
                identifier,
                CustodianFormat.readNumber(cells[UNITS], "units"),
                CustodianFormat.readNumber(cells[BOOK_COST], "book cost"));
    }
}
