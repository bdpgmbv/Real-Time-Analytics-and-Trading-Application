package vyshaliprabananthlal.ingest.receive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.Acknowledgment;

class KafkaBatchTest {

  @Test
  @DisplayName("counts the rows the database says it changed")
  void countsRowsChanged() {
    assertThat(KafkaBatch.howManyRowsChanged(new int[] {1, 1, 1})).isEqualTo(3);
    assertThat(KafkaBatch.howManyRowsChanged(new int[] {1, 0, 1})).isEqualTo(2);
    assertThat(KafkaBatch.howManyRowsChanged(new int[] {})).isZero();
  }

  @Test
  @DisplayName("a batch result of SUCCESS_NO_INFO is not counted as negative")
  void noInfoIsNotNegative() {
    assertThat(KafkaBatch.howManyRowsChanged(new int[] {-2, -2})).isZero();
    assertThat(KafkaBatch.howManyRowsChanged(new int[] {-3, 5})).isEqualTo(5);
  }

  @Test
  @DisplayName("the database is written before Kafka is told, never the other way round")
  void theDatabaseIsWrittenFirst() {
    JdbcTemplate database = mock(JdbcTemplate.class);
    Acknowledgment kafka = mock(Acknowledgment.class);
    when(database.batchUpdate(Mockito.anyString(), Mockito.<List<Object[]>>any()))
        .thenReturn(new int[] {1});

    new KafkaBatch(database, new SimpleMeterRegistry())
        .writeThenAcknowledge("test", "UPDATE x", List.<Object[]>of(new Object[] {1}), kafka);

    InOrder whatHappenedFirst = Mockito.inOrder(database, kafka);
    whatHappenedFirst
        .verify(database)
        .batchUpdate(Mockito.anyString(), Mockito.<List<Object[]>>any());
    whatHappenedFirst.verify(kafka).acknowledge();
  }

  @Test
  @DisplayName("a write that fails leaves Kafka un-acknowledged so the batch comes back")
  void aFailedWriteDoesNotAcknowledge() {
    JdbcTemplate database = mock(JdbcTemplate.class);
    Acknowledgment kafka = mock(Acknowledgment.class);
    when(database.batchUpdate(Mockito.anyString(), Mockito.<List<Object[]>>any()))
        .thenThrow(new IllegalStateException("the database went away"));

    KafkaBatch batch = new KafkaBatch(database, new SimpleMeterRegistry());

    try {
      batch.writeThenAcknowledge("test", "UPDATE x", List.<Object[]>of(new Object[] {1}), kafka);
    } catch (IllegalStateException expected) {
      assertThat(expected).hasMessage("the database went away");
    }

    verify(kafka, Mockito.never()).acknowledge();
  }
}
