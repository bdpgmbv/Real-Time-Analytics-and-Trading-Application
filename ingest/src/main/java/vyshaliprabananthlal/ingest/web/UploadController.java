package vyshaliprabananthlal.ingest.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vyshaliprabananthlal.ingest.custodian.BadLine;
import vyshaliprabananthlal.ingest.custodian.FileLoader;

@RestController
public class UploadController {

  private final FileLoader loader;

  public UploadController(FileLoader loader) {
    this.loader = loader;
  }

  @PostMapping("/upload")
  public ResponseEntity<UploadAnswer> uploadACustodianFile(@RequestParam("file") MultipartFile file)
      throws IOException {

    String fileName = nameOrUnnamed(file.getOriginalFilename());
    String contents = new String(file.getBytes(), StandardCharsets.UTF_8);

    try {
      FileLoader.LoadResult result = loader.load(fileName, contents, "UI UPLOAD");

      if (result.wasAlreadySeen()) {
        return ResponseEntity.ok(
            new UploadAnswer(
                fileName, "This file has been uploaded before. Nothing changed.", 0, 0, List.of()));
      }

      return ResponseEntity.ok(
          new UploadAnswer(
              fileName,
              "Loaded from " + result.custodian() + ".",
              result.rowsLoaded(),
              result.rowsRejected(),
              loader.problemsFrom(result.fileLoadId())));

    } catch (BadLine wholeFileIsWrong) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new UploadAnswer(fileName, wholeFileIsWrong.whatIsWrong(), 0, 0, List.of()));
    }
  }

  private static String nameOrUnnamed(@Nullable String givenName) {
    if (givenName == null || givenName.isBlank()) {
      return "unnamed";
    }
    return givenName;
  }

  public record UploadAnswer(
      String fileName, String message, int rowsLoaded, int rowsRejected, List<String> problems) {}
}
