package ch.so.agi.hop.geotools.vector;

final class GeoToolsRuntimeSupport {
  private GeoToolsRuntimeSupport() {}

  static void initialize() {
    ch.so.agi.hop.geotools.common.GeoToolsRuntimeSupport.initialize();
  }
}
