package ch.so.agi.hop.vector.core;

public enum VectorFormat {
  SHAPEFILE,
  GEOPACKAGE,
  ARCINFO_GENERATE;

  public boolean writable() {
    return true;
  }

  public boolean supportsAttributes() {
    return this != ARCINFO_GENERATE;
  }

  public boolean supportsLayerSelection() {
    return this == GEOPACKAGE;
  }

  public boolean readable() {
    return this != ARCINFO_GENERATE;
  }

  public boolean requiresGeometrySample() {
    return this != ARCINFO_GENERATE;
  }

  public static VectorFormat resolve(String configured, java.nio.file.Path file) {
    if (configured != null && !configured.isBlank() && !configured.equals("AUTO"))
      return valueOf(configured);
    String name = file.toString().toLowerCase(java.util.Locale.ROOT);
    if (name.endsWith(".shp")) return SHAPEFILE;
    if (name.endsWith(".gpkg")) return GEOPACKAGE;
    if (name.endsWith(".gen")) return ARCINFO_GENERATE;
    throw new IllegalArgumentException("Select a vector format or use .shp, .gpkg or .gen");
  }
}
