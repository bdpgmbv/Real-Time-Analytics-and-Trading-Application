package vyshaliprabananthlal.ingest.format;

public interface CustodianFormat {

  String custodianName();

  boolean matches(String headingLine);

  PositionRow readOneLine(String line);
}
