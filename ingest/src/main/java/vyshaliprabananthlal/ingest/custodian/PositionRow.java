package vyshaliprabananthlal.ingest.custodian;

public record PositionRow(
    String accountName, String identifier, double howMany, double whatWePaid) {}
