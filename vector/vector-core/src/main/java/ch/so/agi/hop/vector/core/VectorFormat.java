package ch.so.agi.hop.vector.core;

public enum VectorFormat {
  SHAPEFILE,
  GEOPACKAGE,
  FILEGEODATABASE,
  ARCINFO_GENERATE,
  FLATGEOBUF,
  PARQUET,
  GEOJSON;

  public boolean writable() {
    return true;
  }

  public boolean supportsAttributes() {
    return this != ARCINFO_GENERATE;
  }

  public boolean supportsLayerSelection() {
    return this == GEOPACKAGE || this == FILEGEODATABASE;
  }

  public boolean readable() {
    return this == SHAPEFILE || this == GEOPACKAGE || this == FILEGEODATABASE || this == GEOJSON;
  }

  public boolean requiresGeometrySample() {
    return this != ARCINFO_GENERATE && this != GEOJSON;
  }

  public static VectorFormat resolve(String configured, java.nio.file.Path file) {
    if (configured != null && !configured.isBlank() && !configured.equals("AUTO"))
      return valueOf(configured);
    String name = file.toString().toLowerCase(java.util.Locale.ROOT);
    if (name.endsWith(".shp")) return SHAPEFILE;
    if (name.endsWith(".gpkg")) return GEOPACKAGE;
    if (name.endsWith(".gdb")) return FILEGEODATABASE;
    if (name.endsWith(".fgb")) return FLATGEOBUF;
    if (name.endsWith(".parquet")) return PARQUET;
    if (name.endsWith(".gen")) return ARCINFO_GENERATE;
    if (name.endsWith(".geojson") || name.endsWith(".json")) return GEOJSON;
    throw new IllegalArgumentException(
        "Select a vector format or use .shp, .gpkg, .gdb, .gen, .fgb, .parquet, .geojson or .json");
  }
}
