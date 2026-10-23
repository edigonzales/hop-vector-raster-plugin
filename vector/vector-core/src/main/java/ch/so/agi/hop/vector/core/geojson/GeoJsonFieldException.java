package ch.so.agi.hop.vector.core.geojson;

/** A row data error associated with a configured source field. */
public final class GeoJsonFieldException extends IllegalArgumentException {
  private final String field;

  public GeoJsonFieldException(String field, String message, Throwable cause) {
    super(message, cause);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
