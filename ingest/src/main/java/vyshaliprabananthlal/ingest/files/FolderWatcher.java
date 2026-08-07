package vyshaliprabananthlal.ingest.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vyshaliprabananthlal.ingest.format.BadLine;

@Component
public class FolderWatcher {

  private static final Logger LOG = LoggerFactory.getLogger(FolderWatcher.class);

  private final FileLoader loader;
  private final Path folderToWatch;
  private final Path folderForFinishedFiles;

  public FolderWatcher(
      FileLoader loader,
      @Value("${rtat.custodian-files.incoming}") String incoming,
      @Value("${rtat.custodian-files.finished}") String finished) {

    this.loader = loader;
    this.folderToWatch = Path.of(incoming);
    this.folderForFinishedFiles = Path.of(finished);
  }

  @Scheduled(fixedDelayString = "${rtat.custodian-files.look-every-milliseconds}")
  public void lookForNewFiles() {
    if (!Files.isDirectory(folderToWatch)) {
      return;
    }

    for (Path file : filesWaiting()) {
      loadOneFile(file);
    }
  }

  List<Path> filesWaiting() {
    try (var everything = Files.list(folderToWatch)) {
      return everything.filter(Files::isRegularFile).sorted().toList();
    } catch (IOException problem) {
      LOG.error("could not look inside {}: {}", folderToWatch, problem.getMessage());
      return List.of();
    }
  }

  void loadOneFile(Path file) {
    try {
      String contents = Files.readString(file, StandardCharsets.UTF_8);
      String name = nameOf(file);
      LoadResult result = loader.load(name, contents, "SFTP");

      if (result.wasAlreadySeen()) {
        LOG.info("{} was loaded before, moving it aside", name);
      } else {
        LOG.info(
            "{}: {} rows loaded, {} rejected", name, result.rowsLoaded(), result.rowsRejected());
      }
      moveAside(file);

    } catch (IOException couldNotRead) {
      LOG.error("could not read {}: {}", file, couldNotRead.getMessage());
    } catch (BadLine wholeFileIsWrong) {
      LOG.error("{} was refused: {}", nameOf(file), wholeFileIsWrong.whatIsWrong());
      moveAside(file);
    }
  }

  static String nameOf(Path file) {
    Path justTheName = file.getFileName();
    return justTheName == null ? file.toString() : justTheName.toString();
  }

  private void moveAside(Path file) {
    try {
      Files.createDirectories(folderForFinishedFiles);
      Files.move(file, folderForFinishedFiles.resolve(nameOf(file)));
    } catch (IOException couldNotMove) {
      LOG.error("could not move {} aside: {}", file, couldNotMove.getMessage());
    }
  }
}
