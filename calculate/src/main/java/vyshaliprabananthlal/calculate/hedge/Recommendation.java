package vyshaliprabananthlal.calculate.hedge;

public record Recommendation(
    String currency, double exposure, double weSuggest, String instrument, String why) {}
