package vyshaliprabananthlal.contract.codec;

import com.google.protobuf.ByteString;
import java.math.BigDecimal;
import java.math.BigInteger;
import vyshaliprabananthlal.common.money.Ccy;
import vyshaliprabananthlal.contract.v1.Decimal;

public final class DecimalCodec {
  private DecimalCodec() {}

  public static Decimal toProto(BigDecimal value) {
    byte[] unscaled = value.unscaledValue().toByteArray();
    return Decimal.newBuilder()
        .setUnscaled(ByteString.copyFrom(unscaled))
        .setScale(value.scale())
        .build();
  }

  public static BigDecimal fromProto(Decimal proto) {
    ByteString unscaled = proto.getUnscaled();
    if (unscaled.isEmpty()) {
      return BigDecimal.valueOf(0, proto.getScale());
    }
    BigInteger value = new BigInteger(unscaled.toByteArray());
    return new BigDecimal(value, proto.getScale());
  }

  public static vyshaliprabananthlal.contract.v1.Money toProto(
      vyshaliprabananthlal.common.money.Money money) {
    return vyshaliprabananthlal.contract.v1.Money.newBuilder()
        .setCcy(money.ccy().code())
        .setAmount(toProto(money.amount()))
        .build();
  }

  public static vyshaliprabananthlal.common.money.Money fromProto(
      vyshaliprabananthlal.contract.v1.Money proto) {
    Ccy ccy = Ccy.of(proto.getCcy());
    BigDecimal amount = fromProto(proto.getAmount());
    return new vyshaliprabananthlal.common.money.Money(ccy, amount);
  }
}
