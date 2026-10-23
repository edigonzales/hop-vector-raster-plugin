package ch.so.agi.hop.vector.core;

import ch.so.agi.hop.vector.core.geojson.GeoJsonFeatureOptions;

/** GeoJSON feature mapping and transactional output policy. */
public record GeoJsonOptions(GeoJsonFeatureOptions features, boolean overwrite)
    implements FormatOptions {
  public GeoJsonOptions {
    if (features == null) features = GeoJsonFeatureOptions.defaults();
  }

  public static GeoJsonOptions defaults() {
    return new GeoJsonOptions(GeoJsonFeatureOptions.defaults(), false);
  }
}
