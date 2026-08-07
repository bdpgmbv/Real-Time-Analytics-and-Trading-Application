package vyshaliprabananthlal.ingest.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import vyshaliprabananthlal.ingest.files.FileLoader;
import vyshaliprabananthlal.ingest.files.LoadResult;
import vyshaliprabananthlal.ingest.format.BadLine;
import vyshaliprabananthlal.ingest.web.UploadController.UploadAnswer;

class UploadControllerTest {

  private FileLoader loader;
  private UploadController controller;

  @BeforeEach
  void setUp() {
    loader = mock(FileLoader.class);
    controller = new UploadController(loader);
  }

  @Test
  @DisplayName("a good file comes back with the counts and the custodian name")
  void aGoodUploadReportsWhatHappened() throws Exception {
    when(loader.load(any(), any(), eq("UI UPLOAD")))
        .thenReturn(new LoadResult(7, "Northgate Trust", 50, 50, 0, false));
    when(loader.problemsFrom(7)).thenReturn(List.of());

    ResponseEntity<UploadAnswer> answer = controller.uploadACustodianFile(aFile("positions.csv"));

    UploadAnswer body = bodyOf(answer);

    assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(body.rowsLoaded()).isEqualTo(50);
    assertThat(body.rowsRejected()).isZero();
    assertThat(body.message()).contains("Northgate Trust");
  }

  @Test
  @DisplayName("the upload is marked as coming from the screen, not from SFTP")
  void uploadsAreMarkedAsComingFromTheScreen() throws Exception {
    when(loader.load(any(), any(), any()))
        .thenReturn(new LoadResult(1, "Northgate Trust", 1, 1, 0, false));
    when(loader.problemsFrom(1)).thenReturn(List.of());

    controller.uploadACustodianFile(aFile("positions.csv"));

    verify(loader).load(eq("positions.csv"), any(), eq("UI UPLOAD"));
  }

  @Test
  @DisplayName("rejected rows are handed back so the person can see what to fix")
  void rejectedRowsComeBackToTheUser() throws Exception {
    when(loader.load(any(), any(), any()))
        .thenReturn(new LoadResult(9, "Northgate Trust", 50, 47, 3, false));
    when(loader.problemsFrom(9))
        .thenReturn(
            List.of(
                "line 12: the quantity is not a number: ABC",
                "line 30: the identifier must be 9 characters, found 5",
                "line 44: no such account or security"));

    ResponseEntity<UploadAnswer> answer = controller.uploadACustodianFile(aFile("messy.csv"));

    UploadAnswer body = bodyOf(answer);

    assertThat(body.rowsLoaded()).isEqualTo(47);
    assertThat(body.rowsRejected()).isEqualTo(3);
    assertThat(body.problems()).hasSize(3);
    assertThat(body.problems().get(0)).contains("line 12");
  }

  @Test
  @DisplayName("uploading the same file twice says so plainly and changes nothing")
  void aRepeatUploadSaysSo() throws Exception {
    when(loader.load(any(), any(), any())).thenReturn(new LoadResult(3, "", 0, 0, 0, true));

    ResponseEntity<UploadAnswer> answer = controller.uploadACustodianFile(aFile("again.csv"));

    UploadAnswer body = bodyOf(answer);

    assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(body.message()).contains("uploaded before");
    assertThat(body.rowsLoaded()).isZero();
  }

  @Test
  @DisplayName("a file no custodian format matches comes back as a bad request, not a crash")
  void anUnreadableFileIsARejection() throws Exception {
    when(loader.load(any(), any(), any()))
        .thenThrow(new BadLine("no custodian format matches this heading: who,knows,what"));

    ResponseEntity<UploadAnswer> answer = controller.uploadACustodianFile(aFile("odd.csv"));

    UploadAnswer body = bodyOf(answer);

    assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(body.message()).contains("no custodian format matches");
  }

  @Test
  @DisplayName("a file uploaded with no name still works")
  void aFileWithNoNameStillWorks() throws Exception {
    when(loader.load(eq("unnamed"), any(), any()))
        .thenReturn(new LoadResult(1, "Northgate Trust", 1, 1, 0, false));
    when(loader.problemsFrom(1)).thenReturn(List.of());

    MockMultipartFile noName =
        new MockMultipartFile(
            "file", null, "text/csv", "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8));

    ResponseEntity<UploadAnswer> answer = controller.uploadACustodianFile(noName);

    assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(loader).load(eq("unnamed"), any(), eq("UI UPLOAD"));
  }

  private UploadAnswer bodyOf(ResponseEntity<UploadAnswer> answer) {
    UploadAnswer body = answer.getBody();
    if (body == null) {
      throw new AssertionError("the response had no body");
    }
    return body;
  }

  private MockMultipartFile aFile(String name) {
    String contents = "account,identifier,quantity,cost\nACCOUNT-1,000000001,500,12000\n";
    return new MockMultipartFile(
        "file", name, "text/csv", contents.getBytes(StandardCharsets.UTF_8));
  }
}
