package ch.so.agi.hop.vector.core;

/** Explicit creation/append behavior; existing indexes are always maintained. */
public record GeoPackageOptions(WriteMode writeMode, boolean createSpatialIndex)
    implements FormatOptions {
  public enum WriteMode {
    CREATE_FILE,
    ADD_LAYER,
    APPEND_FEATURES
  }

  public GeoPackageOptions {
    if (writeMode == null) writeMode = WriteMode.CREATE_FILE;
  }

  public static GeoPackageOptions defaults() {
    return new GeoPackageOptions(WriteMode.CREATE_FILE, true);
  }
}
