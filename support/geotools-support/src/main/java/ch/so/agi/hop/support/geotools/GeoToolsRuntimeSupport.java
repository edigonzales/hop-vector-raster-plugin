package ch.so.agi.hop.support.geotools;

/** Shared initialization lives with the GeoTools runtime, independently of vector formats. */
public final class GeoToolsRuntimeSupport {
  private GeoToolsRuntimeSupport() {}
  public static void initialize() {
    ch.so.agi.hop.raster.geotools.GeoToolsRuntimeSupport.initialize();
  }
}
