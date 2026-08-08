package vyshaliprabananthlal.ingest.format;

/**
 * How one custodian lays out a file. Adding a third custodian is one new class
 * implementing this, and nothing else.
 */
public interface CustodianFormat {

    String custodianName();

    /** True if this heading line is one of ours. */
    boolean matches(String headingLine);

    /** Reads one line, or throws BadLine saying what is wrong with it. */
    PositionRow readOneLine(String line);

    /** One holding, as it appeared in the file. */
    record PositionRow(String accountName, String identifier, double quantity, double cost) {}

    /** Thrown for one bad line. The file keeps loading; this row does not. */
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
