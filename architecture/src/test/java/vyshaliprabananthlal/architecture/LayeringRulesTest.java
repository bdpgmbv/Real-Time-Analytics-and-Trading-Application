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

@AnalyzeClasses(
    packages = "vyshaliprabananthlal",
    importOptions = LayeringRulesTest.ExcludeGeneratedProtobuf.class)
class LayeringRulesTest {
  static final class ExcludeGeneratedProtobuf implements ImportOption {
    @Override
    public boolean includes(Location location) {
      return !location.contains("/vyshaliprabananthlal/contract/v1/");
    }
  }

  @ArchTest
  static final ArchRule domainHasNoFrameworkDependencies =
      noClasses()
          .that()
          .resideInAPackage("vyshaliprabananthlal.common..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..", "org.apache.flink..", "com.google.protobuf..", "jakarta..");

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

  @ArchTest static final ArchRule noConsoleOutput = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

  @ArchTest static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

  @ArchTest static final ArchRule noGenericExceptions = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
}
