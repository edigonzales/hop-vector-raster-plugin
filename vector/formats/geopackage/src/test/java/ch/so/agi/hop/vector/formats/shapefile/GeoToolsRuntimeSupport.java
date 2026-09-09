package ch.so.agi.hop.vector.formats.shapefile;

public final class GeoToolsRuntimeSupport {
  private GeoToolsRuntimeSupport() {}

  public static void initialize() {
    ch.so.agi.hop.support.geotools.GeoToolsRuntimeSupport.initialize();
  }
}
