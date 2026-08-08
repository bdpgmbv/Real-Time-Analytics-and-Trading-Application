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
import vyshaliprabananthlal.ingest.format.CustodianFormat;
import vyshaliprabananthlal.platform.lock.AdvisoryLock;

@Component
public class FolderWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(FolderWatcher.class);

    private static final String ONE_SWEEP_AT_A_TIME = "sweep-the-custodian-folder";

    private final AdvisoryLock lock;
    private final FileLoader loader;
    private final Path folderToWatch;
    private final Path folderForFinishedFiles;

    public FolderWatcher(
            AdvisoryLock lock,
            FileLoader loader,
            @Value("${rtat.custodian-files.incoming}") String incoming,
            @Value("${rtat.custodian-files.finished}") String finished) {

        this.lock = lock;
        this.loader = loader;
        this.folderToWatch = Path.of(incoming);
        this.folderForFinishedFiles = Path.of(finished);
    }

    /**
     * The folder is shared, so only one instance may sweep it at a time. Two instances reading the
     * same file would both load it; the fingerprint would catch the second, but only after both
     * had read and parsed it, and both had moved it.
     */
    @Scheduled(fixedDelayString = "${rtat.custodian-files.look-every-milliseconds}")
    public void lookForNewFiles() {
        if (!Files.isDirectory(folderToWatch)) {
            return;
        }

        lock.runExclusively(ONE_SWEEP_AT_A_TIME, () -> {
            for (Path file : waitingFiles()) {
                loadOneFile(file);
            }
        });
    }

    List<Path> waitingFiles() {
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
            String name = fileNameOf(file);
            FileLoader.LoadResult result = loader.load(name, contents, "SFTP");

            if (result.wasAlreadySeen()) {
                LOG.info("{} was loaded before, moving it aside", name);
            } else {
                LOG.info("{}: {} rows loaded, {} rejected", name, result.rowsLoaded(), result.rowsRejected());
            }
            moveToFinished(file);

        } catch (IOException couldNotRead) {
            LOG.error("could not read {}: {}", file, couldNotRead.getMessage());
        } catch (CustodianFormat.BadLine wholeFileIsWrong) {
            LOG.error("{} was refused: {}", fileNameOf(file), wholeFileIsWrong.reason());
            moveToFinished(file);
        }
    }

    static String fileNameOf(Path file) {
        Path justTheName = file.getFileName();
        return justTheName == null ? file.toString() : justTheName.toString();
    }

    private void moveToFinished(Path file) {
        try {
            Files.createDirectories(folderForFinishedFiles);
            Files.move(file, folderForFinishedFiles.resolve(fileNameOf(file)));
        } catch (IOException couldNotMove) {
            LOG.error("could not move {} aside: {}", file, couldNotMove.getMessage());
        }
    }
}
