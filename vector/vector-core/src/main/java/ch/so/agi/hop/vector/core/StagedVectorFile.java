package ch.so.agi.hop.vector.core;

import java.io.IOException;
import java.nio.file.*;

/** Owns a private staging directory beside the target, so publication stays on one filesystem. */
public final class StagedVectorFile implements AutoCloseable {
  public final Path directory, file;
  private final Path target;
  private final boolean overwrite;
  private boolean published;

  public StagedVectorFile(Path target, boolean overwrite) throws IOException {
    this.target = target.toAbsolutePath();
    this.overwrite = overwrite;
    if (Files.exists(this.target) && (!overwrite || !Files.isRegularFile(this.target)))
      throw new FileAlreadyExistsException(target.toString());
    directory = Files.createTempDirectory(this.target.getParent(), ".vector-export-");
    file = directory.resolve("output");
  }

  public void publish() throws IOException {
    checkCancelled();
    if (published) throw new IOException("Output already published");
    // Without overwrite, a hard link atomically fails if another writer created the target.
    if (!overwrite) {
      Files.createLink(target, file);
      Files.delete(file);
    } else
      Files.move(file, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    published = true;
  }

  public static void checkCancelled() throws java.io.InterruptedIOException {
    if (Thread.currentThread().isInterrupted())
      throw new java.io.InterruptedIOException("Vector export stopped");
  }

  public void close() throws IOException {
    try (var paths = Files.walk(directory)) {
      for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList())
        Files.deleteIfExists(p);
    }
  }
}
