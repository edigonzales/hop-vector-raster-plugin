package ch.so.agi.hop.vector.formats.shapefile;

/**
 * Signals an error while reading, writing or mapping a Shapefile dataset.
 *
 * <p>This is the shared, checked error type for the whole {@code
 * ch.so.agi.hop.vector.formats.shapefile} package and its sub-packages. It extends {@link
 * IoxException} so it integrates cleanly with the IOX reader/writer flow used by the generic
 * transformation engine.
 *
 * <p>Messages should be precise and actionable and follow the {@code SHP input '<id>': ...} /
 * {@code SHP output '<id>': ...} style described in the Shapefile specification.
 */
public class ShapefileMappingException extends java.io.IOException {

  public ShapefileMappingException(String message) {
    super(message);
  }

  public ShapefileMappingException(String message, Throwable cause) {
    super(message, cause);
  }
}
