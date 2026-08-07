package vyshaliprabananthlal.ingest.custodian;

public interface CustodianFormat {

  String custodianName();

  boolean looksLikeMine(String headingLine);

  PositionRow readOneLine(String line);
}
