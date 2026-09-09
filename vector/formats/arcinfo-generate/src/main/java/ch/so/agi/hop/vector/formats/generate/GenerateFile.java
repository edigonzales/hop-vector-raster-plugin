package ch.so.agi.hop.vector.formats.generate;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.locationtech.jts.geom.Geometry;

/** A failed/cancelled export never replaces a previously successful destination. */
public final class GenerateFile implements AutoCloseable {
  private static final Set<Path> ACTIVE = ConcurrentHashMap.newKeySet();
  private final Path output;
  private final Path temporary;
  private final boolean overwrite;
  private final GenerateEncoder encoder;
  private final BufferedWriter writer;
  private boolean closed;

  public GenerateFile(Path output, boolean overwrite, GenerateEncoder.Options options)
      throws IOException {
    Path absolute = output.toAbsolutePath().normalize();
    this.output = absolute.getParent().toRealPath().resolve(absolute.getFileName());
    this.overwrite = overwrite;
    encoder = new GenerateEncoder(options);
    if (!ACTIVE.add(this.output))
      throw new IOException("Another GENERATE writer owns this output path");
    Path temp = null;
    try {
      if (!overwrite && Files.exists(this.output))
        throw new IOException("Output already exists: " + this.output);
      temp = Files.createTempFile(this.output.getParent(), ".hop-generate-", ".gen");
      writer = Files.newBufferedWriter(temp, StandardCharsets.US_ASCII);
      temporary = temp;
    } catch (IOException | RuntimeException e) {
      ACTIVE.remove(this.output);
      if (temp != null) Files.deleteIfExists(temp);
      throw e;
    }
  }

  public void write(long id, Geometry geometry) throws IOException {
    encoder.writeFeature(writer, id, geometry);
  }

  public void commit() throws IOException {
    if (closed) throw new IOException("Writer is closed");
    try {
      encoder.finish(writer);
      writer.close();
      if (overwrite) Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
      else Files.move(temporary, output);
    } finally {
      close();
    }
  }

  @Override
  public void close() throws IOException {
    if (!closed) {
      closed = true;
      try {
        writer.close();
      } finally {
        try {
          Files.deleteIfExists(temporary);
        } finally {
          ACTIVE.remove(output);
        }
      }
    }
  }
}
