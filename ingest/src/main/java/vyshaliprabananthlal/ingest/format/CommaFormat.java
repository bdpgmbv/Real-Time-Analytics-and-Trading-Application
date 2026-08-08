package vyshaliprabananthlal.ingest.format;

import org.springframework.stereotype.Component;

/** Northgate Trust: comma separated, account first, quantity before cost. */
@Component
public class CommaFormat implements CustodianFormat {

    private static final String HEADING = "account,identifier,quantity,cost";
    private static final int ACCOUNT = 0;
    private static final int IDENTIFIER = 1;
    private static final int QUANTITY = 2;
    private static final int COST = 3;

    @Override
    public String custodianName() {
        return "Northgate Trust";
    }

    @Override
    public boolean matches(String headingLine) {
        return HEADING.equalsIgnoreCase(headingLine.trim());
    }

    @Override
    public PositionRow readOneLine(String line) {
        String[] cells = line.split(",", -1);

        if (cells.length != 4) {
            throw new BadLine("expected 4 values separated by commas, found " + cells.length);
        }

        String accountName = cells[ACCOUNT].trim();
        String identifier = cells[IDENTIFIER].trim();
        CustodianFormat.checkAccountAndIdentifier(accountName, identifier, "account name");

        return new PositionRow(
                accountName,
                identifier,
                CustodianFormat.readNumber(cells[QUANTITY], "quantity"),
                CustodianFormat.readNumber(cells[COST], "cost"));
    }
}
