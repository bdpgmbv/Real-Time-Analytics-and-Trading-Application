package vyshaliprabananthlal.ingest.files;

public record LoadResult(
    int fileLoadId,
    String custodian,
    int rowsInFile,
    int rowsLoaded,
    int rowsRejected,
    boolean wasAlreadySeen) {

  public static LoadResult alreadySeen(int fileLoadId) {
    return new LoadResult(fileLoadId, "", 0, 0, 0, true);
  }
}
