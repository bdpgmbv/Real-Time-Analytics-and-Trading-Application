package vyshaliprabananthlal.sim.edgar;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DownloadOne {

  private DownloadOne() {}

  public static void main(String[] args) throws Exception {
    Path out = Path.of(System.getProperty("rtat.dataDir", "data")).resolve("edgar");
    EdgarClient client = new EdgarClient(out);

    Path file = client.fetchSubmissions("0001067983");

    System.out.println("saved " + file.toAbsolutePath() + " (" + Files.size(file) + " bytes)");
  }
}
