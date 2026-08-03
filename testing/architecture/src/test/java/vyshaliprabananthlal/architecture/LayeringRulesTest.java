package vyshaliprabananthlal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.math.BigDecimal;

/**
 * Rules that are cheap to state and expensive to rediscover. Each one encodes a decision that would
 * otherwise survive only in a review comment.
 */
@AnalyzeClasses(
    packages = "vyshaliprabananthlal",
    importOptions = LayeringRulesTest.ExcludeGeneratedProtobuf.class)
class LayeringRulesTest {

  /**
   * Generated protobuf classes are excluded once here rather than in every rule. They are not ours
   * to shape, and exempting them per-rule invites forgetting one.
   */
  static final class ExcludeGeneratedProtobuf implements ImportOption {
    @Override
    public boolean includes(Location location) {
      return !location.contains("/vyshaliprabananthlal/contract/v1/");
    }
  }

  /**
   * The domain must stay plain Java. Once a framework annotation reaches it, the exposure math can
   * no longer be tested without standing something up.
   */
  @ArchTest
  static final ArchRule domainHasNoFrameworkDependencies =
      noClasses()
          .that()
          .resideInAPackage("vyshaliprabananthlal.common..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..", "org.apache.flink..", "com.google.protobuf..", "jakarta..");

  /**
   * Money in binary floating point loses cents at fund notionals. There is no acceptable use of
   * double or float in these packages.
   */
  @ArchTest
  static final ArchRule noBinaryFloatingPointInMoney =
      noFields()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage("..money..")
          .should()
          .haveRawType(double.class)
          .orShould()
          .haveRawType(float.class)
          .orShould()
          .haveRawType(Double.class)
          .orShould()
          .haveRawType(Float.class);

  /** java.util.Date and Calendar are mutable and time-zone ambiguous. Use java.time. */
  @ArchTest
  static final ArchRule noLegacyDateApi =
      noClasses()
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("java.util.Date")
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName("java.util.Calendar")
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName("java.sql.Date");

  /** Monetary amounts are BigDecimal end to end. */
  @ArchTest
  static final ArchRule monetaryFieldsAreBigDecimal =
      fields()
          .that()
          .haveName("amount")
          .and()
          .areDeclaredInClassesThat()
          .resideInAPackage("vyshaliprabananthlal.common..")
          .should()
          .haveRawType(BigDecimal.class);

  /** Diagnostics belong in the log pipeline, where they carry a correlation id. */
  @ArchTest static final ArchRule noConsoleOutput = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

  @ArchTest static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

  /** A bare RuntimeException tells a caller nothing about what to do next. */
  @ArchTest static final ArchRule noGenericExceptions = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
}
