package ch.so.agi.hop.raster.values;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;

/** Formats sparse Basic-level messages for the raster writer's TIFF encoder progress. */
final class RasterWriteProgress {
  private final Consumer<String> basicLog;
  private Path output;
  private String compression;
  private long startedAt;
  private int lastMilestone;

  RasterWriteProgress(Consumer<String> basicLog) {
    this.basicLog = basicLog;
  }

  void started(Path output, String compression) {
    this.output = output;
    this.compression = compression == null || compression.isBlank() ? "Deflate" : compression;
    startedAt = System.nanoTime();
    lastMilestone = 0;
    basicLog.accept(
        "Raster Writer: writing " + output + " with " + this.compression + " compression");
  }

  void report(int percentage) {
    int value = Math.max(0, Math.min(100, percentage));
    int milestone = value / 10 * 10;
    if (milestone >= lastMilestone + 10) {
      lastMilestone = milestone;
      basicLog.accept("Raster Writer: " + output + " encoded " + milestone + "%");
    }
  }

  void completed() {
    long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    basicLog.accept(
        "Raster Writer: wrote "
            + output
            + " with "
            + compression
            + " compression in "
            + elapsedMillis
            + " ms");
  }
}
