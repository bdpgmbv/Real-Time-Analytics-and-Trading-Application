package vyshaliprabananthlal.ingest.format;

/**
 * How one custodian lays out a file. Adding a third custodian is one new class implementing
 * this, and nothing else: FileLoader is handed every implementation and asks each in turn
 * whether the heading line is theirs.
 */
public interface CustodianFormat {

    /** Who sent it. Recorded against the load so a problem can be taken up with them. */
    String custodianName();

    /** True if this heading line is one of ours. */
    boolean matches(String headingLine);

    /** Reads one line, or throws {@link BadLine} saying what is wrong with it. */
    PositionRow readOneLine(String line);

    /**
     * Reads one cell as a number, naming the field if it is not one.
     *
     * <p>Shared here rather than on one of the formats, so that no format has to depend on
     * another format to read a number.
     */
    static double readNumber(String cell, String fieldName) {
        try {
            return Double.parseDouble(cell.trim());
        } catch (NumberFormatException notANumber) {
            throw new BadLine("the " + fieldName + " is not a number: " + cell.trim());
        }
    }

    /** Checks the parts of a line every custodian has to get right. */
    static void checkAccountAndIdentifier(String accountName, String identifier, String accountFieldName) {
        if (accountName.isEmpty()) {
            throw new BadLine("the " + accountFieldName + " is empty");
        }
        if (identifier.length() != 9) {
            throw new BadLine("the identifier must be 9 characters, found " + identifier.length());
        }
    }

    /** One holding, as it appeared in the file. */
    record PositionRow(String accountName, String identifier, double quantity, double cost) {}

    /**
     * Thrown for one bad line. The rest of the file keeps loading; this row does not.
     *
     * <p>The reason travels back to whoever uploaded the file, so it says what to fix rather
     * than what went wrong internally.
     */
    class BadLine extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String reason;

        public BadLine(String reason) {
            super(reason);
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }
}
